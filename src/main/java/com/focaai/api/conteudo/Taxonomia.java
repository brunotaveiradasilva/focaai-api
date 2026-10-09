package com.focaai.api.conteudo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import tools.jackson.databind.JsonNode;

/**
 * Árvore mestra de conteúdos (matéria → grupo → assunto) e a leitura dos mapeamentos de edital. Os
 * ids dos assuntos são {@code materia.grupo.assunto}, então as partes dão para ler até em ids que a
 * taxonomia ainda não lista.
 */
class Taxonomia {

    record Assunto(String id, String nome, String grupoId, String materiaId) {
    }

    private final List<Assunto> assuntos = new ArrayList<>();
    private final Map<String, Assunto> assuntoPorId = new HashMap<>();
    private final Map<String, String> nomeDaMateria = new HashMap<>();
    private final Map<String, String> nomeDoGrupo = new LinkedHashMap<>();
    // Matérias e grupos citados pelos editais que a taxonomia ainda não tem.
    private final Map<String, String> nomesExtras;

    Taxonomia(JsonNode raiz, Map<String, String> nomesExtras) {
        this.nomesExtras = nomesExtras;
        for (JsonNode m : raiz.get("subjects")) {
            String materia = m.get("id").asString();
            nomeDaMateria.put(materia, m.get("name").asString());
            for (JsonNode g : m.get("groups")) {
                String grupo = g.get("id").asString();
                nomeDoGrupo.put(grupo, g.get("name").asString());
                for (JsonNode t : g.get("topics")) {
                    Assunto a = new Assunto(t.get("id").asString(), t.get("name").asString(), grupo, materia);
                    assuntos.add(a);
                    assuntoPorId.put(a.id(), a);
                }
            }
        }
    }

    static String materiaDoAssunto(String id) {
        return id.split("\\.")[0];
    }

    static String grupoDoAssunto(String id) {
        String[] partes = id.split("\\.");
        return partes.length < 2 ? partes[0] : partes[0] + "." + partes[1];
    }

    String nomeDaMateria(String id) {
        return nomeDaMateria.getOrDefault(id, nomesExtras.getOrDefault(id, id));
    }

    String nomeDoGrupo(String id) {
        return nomeDoGrupo.getOrDefault(id, nomesExtras.getOrDefault(id, id));
    }

    /** Converte um mapeamento de edital (JSON em português, como o Inep/UFMS publicam) em {@link Edital}. */
    Edital edital(String id, JsonNode raw) {
        List<Edital.Obra> obras = new ArrayList<>();
        JsonNode livros = raw.get("obras_obrigatorias");
        if (livros != null) {
            for (JsonNode o : livros) obras.add(new Edital.Obra(o.get("titulo").asString(), o.get("autor").asString()));
        }
        List<Edital.TopicoDoEdital> topicos = new ArrayList<>();
        for (JsonNode t : raw.get("topicos")) {
            String topicId = t.get("topic_id").asString();
            Assunto conhecido = assuntoPorId.get(topicId);
            topicos.add(new Edital.TopicoDoEdital(
                    topicId,
                    conhecido != null ? conhecido.nome() : t.get("nome").asString(),
                    t.get("weight").asDouble(),
                    conhecido != null));
        }
        return new Edital(
                id,
                raw.get("exam").asString(),
                raw.get("fonte").asString(),
                texto(raw, "edital"),
                texto(raw, "verificado_em"),
                texto(raw, "revisado_por"),
                raw.get("aviso").asString(),
                obras,
                topicos);
    }

    /**
     * Todo assunto que o app conhece: a taxonomia na ordem dela, mais os assuntos que só os editais
     * citam, cada um no fim do seu grupo (ou da sua matéria, quando o grupo também é novo).
     */
    List<Assunto> todosOsAssuntos(Collection<Edital> editais) {
        List<Assunto> lista = new ArrayList<>(assuntos);
        Set<String> vistos = new HashSet<>();
        lista.forEach(a -> vistos.add(a.id()));
        for (Edital e : editais) {
            for (Edital.TopicoDoEdital t : e.topics()) {
                if (!vistos.add(t.topicId())) continue;
                Assunto novo = new Assunto(t.topicId(), t.name(), grupoDoAssunto(t.topicId()), materiaDoAssunto(t.topicId()));
                int em = ultimoOnde(lista, a -> a.grupoId().equals(novo.grupoId()));
                if (em < 0) em = ultimoOnde(lista, a -> a.materiaId().equals(novo.materiaId()));
                if (em < 0) lista.add(novo);
                else lista.add(em + 1, novo);
            }
        }
        return lista;
    }

    private static int ultimoOnde(List<Assunto> lista, Predicate<Assunto> condicao) {
        for (int i = lista.size() - 1; i >= 0; i--) {
            if (condicao.test(lista.get(i))) return i;
        }
        return -1;
    }

    private static String texto(JsonNode raw, String campo) {
        JsonNode v = raw.get(campo);
        return v == null || v.isNull() ? null : v.asString();
    }
}
