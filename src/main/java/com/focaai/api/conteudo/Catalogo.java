package com.focaai.api.conteudo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.focaai.api.comum.RecursoNaoEncontradoException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Conteúdo fixo do app: matérias, cursos, calendário de provas, a árvore de conteúdos e o mapeamento
 * de cada edital para ela. Tudo vem dos JSON em {@code resources/conteudo}, que são a fonte da
 * verdade, e fica em memória: muda só com um novo deploy.
 */
@Component
public class Catalogo {

    private final List<Materia> materias;
    private final List<AreaEnem> areas;
    private final List<Curso> cursos;
    private final Map<String, Concorrencia> concorrencia;
    private final List<Prova> provas;
    private final List<Vestibular> vestibulares;
    private final List<Integer> ciclos;
    private final Map<String, String> trilhas;
    private final Map<String, String> editalDaProva;
    private final double pesoSemMapeamento;
    private final Map<String, Edital> editais = new LinkedHashMap<>();
    private final JsonNode provaUfms;

    private final List<Topico> topicos;
    private final Map<String, Topico> topicoPorId = new HashMap<>();
    private final Map<String, Map<String, Double>> pesosPorEdital = new HashMap<>();
    private final Map<String, List<TopicoNoRoteiro>> roteiros = new ConcurrentHashMap<>();

    public Catalogo(ObjectMapper json) {
        JsonNode c = ler(json, "catalogo.json");
        materias = json.convertValue(c.get("materias"), new TypeReference<>() { });
        areas = json.convertValue(c.get("areas"), new TypeReference<>() { });
        cursos = json.convertValue(c.get("cursos"), new TypeReference<>() { });
        concorrencia = json.convertValue(c.get("concorrencia"), new TypeReference<LinkedHashMap<String, Concorrencia>>() { });
        provas = json.convertValue(c.get("provas"), new TypeReference<>() { });
        vestibulares = json.convertValue(c.get("vestibulares"), new TypeReference<>() { });
        ciclos = json.convertValue(c.get("ciclos"), new TypeReference<>() { });
        trilhas = json.convertValue(c.get("trilhas"), new TypeReference<LinkedHashMap<String, String>>() { });
        editalDaProva = json.convertValue(c.get("editalDaProva"), new TypeReference<>() { });
        pesoSemMapeamento = c.get("pesoSemMapeamento").asDouble();
        Map<String, String> hospedeira = json.convertValue(c.get("materiaHospedeira"), new TypeReference<>() { });
        Map<String, String> nomesExtras = json.convertValue(c.get("nomesExtras"), new TypeReference<>() { });
        int aulasPorTopico = c.get("aulasPorTopico").asInt();

        Taxonomia taxonomia = new Taxonomia(ler(json, "taxonomia.json"), nomesExtras);
        editais.put("enem", taxonomia.edital("enem", ler(json, "edital.enem.json")));
        editais.put("ufms-2027", taxonomia.edital("ufms-2027", ler(json, "edital.ufms-2027.json")));
        for (Edital e : editais.values()) {
            Map<String, Double> pesos = new HashMap<>();
            e.topics().forEach(t -> pesos.put(t.topicId(), t.weight()));
            pesosPorEdital.put(e.id(), pesos);
        }
        provaUfms = ler(json, "prova.ufms-2027.json");

        Set<String> comAba = new HashSet<>();
        materias.forEach(m -> comAba.add(m.id()));
        List<Topico> lista = new ArrayList<>();
        for (Taxonomia.Assunto t : taxonomia.todosOsAssuntos(editais.values())) {
            String materia = hospedeira.getOrDefault(t.materiaId(), t.materiaId());
            if (!comAba.contains(materia)) continue;
            String grupo = taxonomia.nomeDoGrupo(t.grupoId());
            boolean hospedado = !materia.equals(t.materiaId());
            String nomeMateria = taxonomia.nomeDaMateria(t.materiaId());
            String rotulo = !hospedado || grupo.equals(nomeMateria) ? grupo : nomeMateria + " · " + grupo;
            lista.add(new Topico(t.id(), materia, t.nome(), aulasPorTopico, t.grupoId(), rotulo, null, false));
        }
        // Ids iguais aos nós de fechamento do roadmap v2 do app, para o progresso continuar valendo.
        for (JsonNode f : c.get("retaFinal")) {
            String materia = f.get("subject").asString();
            lista.add(new Topico(materia + ".sim", materia, f.get("title").asString(), f.get("lessons").asInt(),
                    materia + ".final", "Reta final", f.get("desc").asString(), true));
        }
        topicos = Collections.unmodifiableList(lista);
        topicos.forEach(t -> topicoPorId.put(t.id(), t));
    }

    // -------------------------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------------------------

    public List<Materia> materias() {
        return materias;
    }

    public List<AreaEnem> areas() {
        return areas;
    }

    public List<Curso> cursos() {
        return cursos;
    }

    public Map<String, Concorrencia> concorrencia() {
        return concorrencia;
    }

    public List<Prova> provas() {
        return provas;
    }

    public List<Vestibular> vestibulares() {
        return vestibulares;
    }

    public List<Integer> ciclos() {
        return ciclos;
    }

    public Map<String, String> trilhas() {
        return trilhas;
    }

    public List<Topico> topicos() {
        return topicos;
    }

    public JsonNode provaUfms() {
        return provaUfms;
    }

    public Materia materia(String id) {
        return materias.stream().filter(m -> m.id().equals(id)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matéria não encontrada: " + id));
    }

    public boolean materiaExiste(String id) {
        return materias.stream().anyMatch(m -> m.id().equals(id));
    }

    /** Curso desconhecido cai em "Outro curso / ainda não sei", o último da lista, como no app. */
    public Curso curso(String id) {
        return cursos.stream().filter(x -> x.id().equals(id)).findFirst().orElse(cursos.get(cursos.size() - 1));
    }

    public Concorrencia concorrenciaDoCurso(String cursoId) {
        return concorrencia.get(curso(cursoId).tier());
    }

    public Optional<Topico> topico(String id) {
        return Optional.ofNullable(topicoPorId.get(id));
    }

    public String areaDaMateria(String materiaId) {
        return areas.stream().filter(a -> a.subjects().contains(materiaId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matéria sem área: " + materiaId)).id();
    }

    public List<Edital> editais() {
        return List.copyOf(editais.values());
    }

    public Edital edital(String id) {
        Edital e = editais.get(id);
        if (e == null) throw new RecursoNaoEncontradoException("Edital não encontrado: " + id);
        return e;
    }

    /** Assuntos de um edital numa matéria da taxonomia, do mais pesado para o mais leve. */
    public List<Edital.TopicoDoEdital> topicosDoEdital(String editalId, String materiaId) {
        return edital(editalId).topics().stream()
                .filter(t -> Taxonomia.materiaDoAssunto(t.topicId()).equals(materiaId))
                .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
                .toList();
    }

    // -------------------------------------------------------------------------------------------
    // Das provas que o aluno segue até o peso de cada assunto
    // -------------------------------------------------------------------------------------------

    public Optional<String> editalDaProva(String provaId) {
        return Optional.ofNullable(editalDaProva.get(provaId));
    }

    /** Peso de um assunto numa prova; provas sem edital mapeado contam tudo como "citado em parte". */
    public double pesoNaProva(String provaId, String topicoId) {
        return editalDaProva(provaId)
                .map(e -> pesosPorEdital.get(e).getOrDefault(topicoId, 0.0))
                .orElse(pesoSemMapeamento);
    }

    /** O maior peso entre as provas que o aluno segue. */
    public double pesoCombinado(List<String> provaIds, String topicoId) {
        double peso = 0;
        for (String p : provaIds) peso = Math.max(peso, pesoNaProva(p, topicoId));
        return peso;
    }

    /**
     * Roadmap de uma matéria para as provas que o aluno segue, na ordem de estudo. Assuntos que nenhum
     * desses editais cobre ficam de fora.
     */
    public List<TopicoNoRoteiro> roteiro(String materiaId, List<String> provaIds) {
        String chave = materiaId + "|" + String.join(",", provaIds);
        return roteiros.computeIfAbsent(chave, k -> topicos.stream()
                .filter(t -> t.subject().equals(materiaId))
                .map(t -> noRoteiro(t, provaIds))
                .filter(t -> t.weight() > 0)
                .toList());
    }

    private TopicoNoRoteiro noRoteiro(Topico t, List<String> provaIds) {
        double peso = t.retaFinal() ? 1 : pesoCombinado(provaIds, t.id());
        List<TopicoNoRoteiro.PesoNaProva> porProva = t.retaFinal() ? null : provaIds.stream()
                .map(p -> new TopicoNoRoteiro.PesoNaProva(p, editalDaProva(p).isPresent(), pesoNaProva(p, t.id())))
                .toList();
        return new TopicoNoRoteiro(t.id(), t.subject(), t.title(), t.lessons(), t.groupId(), t.groupName(), t.desc(),
                t.retaFinal(), peso, porProva);
    }

    private static JsonNode ler(ObjectMapper json, String arquivo) {
        try (InputStream in = new ClassPathResource("conteudo/" + arquivo).getInputStream()) {
            return json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Não deu pra ler conteudo/" + arquivo, e);
        }
    }
}
