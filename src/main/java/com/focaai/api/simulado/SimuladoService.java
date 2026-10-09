package com.focaai.api.simulado;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.aluno.AlunoService;
import com.focaai.api.comum.RecursoNaoEncontradoException;
import com.focaai.api.comum.RequisicaoInvalidaException;
import com.focaai.api.conteudo.Catalogo;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Simulados sob medida com questões reais do ENEM: montar, responder, finalizar e consultar. */
@Service
public class SimuladoService {

    static final int SIMULADOS_GUARDADOS = 100;

    private final SimuladoRepository simulados;
    private final BancoDeQuestoes banco;
    private final AlunoService alunos;
    private final Catalogo catalogo;
    private final ObjectMapper json;

    public SimuladoService(SimuladoRepository simulados, BancoDeQuestoes banco, AlunoService alunos, Catalogo catalogo,
            ObjectMapper json) {
        this.simulados = simulados;
        this.banco = banco;
        this.alunos = alunos;
        this.catalogo = catalogo;
        this.json = json;
    }

    /**
     * Monta um simulado novo no idioma estrangeiro do perfil e o deixa em andamento. Um simulado que
     * estava em andamento é descartado, como no app.
     */
    @Transactional
    public SimuladoDoAluno criar(UUID usuarioId, NovoSimulado pedido) {
        Set<String> areasValidas = new HashSet<>();
        catalogo.areas().forEach(a -> areasValidas.add(a.id()));
        if (pedido.areas().isEmpty() || !areasValidas.containsAll(pedido.areas())) {
            throw new RequisicaoInvalidaException("Escolha ao menos uma área válida.");
        }
        String lingua = alunos.dados(usuarioId).perfil().language();
        Set<String> vistas = new HashSet<>(simulados.questoesVistas(usuarioId));
        List<QuestaoEnem> questoes = banco.montarSimulado(pedido.areas(), pedido.count(),
                !"todos".equals(pedido.range()), lingua, vistas);
        if (questoes.isEmpty()) throw new RequisicaoInvalidaException("Não encontramos questões para esse simulado.");

        simulados.findByUsuarioIdAndFinalizadoEmIsNull(usuarioId).ifPresent(simulados::delete);
        simulados.flush();
        Simulado novo = simulados.save(new Simulado(UUID.randomUUID(), usuarioId, Instant.now(),
                json.writeValueAsString(pedido.areas()), json.writeValueAsString(questoes)));
        return doAluno(novo);
    }

    @Transactional(readOnly = true)
    public Optional<SimuladoDoAluno> emAndamento(UUID usuarioId) {
        return simulados.findByUsuarioIdAndFinalizadoEmIsNull(usuarioId).map(this::doAluno);
    }

    /** Marca (ou, com {@code letra} nula, apaga) a resposta de uma questão do simulado em andamento. */
    @Transactional
    public SimuladoDoAluno responder(UUID usuarioId, String chave, String letra) {
        Simulado s = buscarEmAndamento(usuarioId);
        QuestaoEnem questao = questoesDe(s).stream().filter(q -> q.key().equals(chave)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Questão fora deste simulado: " + chave));
        if (letra != null && questao.alternatives().stream().noneMatch(a -> a.letter().equals(letra))) {
            throw new RequisicaoInvalidaException("Alternativa inexistente: " + letra);
        }
        Map<String, String> respostas = respostasDe(s);
        if (letra == null) respostas.remove(chave);
        else respostas.put(chave, letra);
        s.setRespostas(json.writeValueAsString(respostas));
        return doAluno(s);
    }

    /** Tempo de prova até agora, em segundos (o app manda de tempos em tempos). */
    @Transactional
    public void registrarTempo(UUID usuarioId, int segundos) {
        buscarEmAndamento(usuarioId).setSegundos(segundos);
    }

    @Transactional
    public SimuladoDoAluno finalizar(UUID usuarioId) {
        Simulado s = buscarEmAndamento(usuarioId);
        s.finalizar(Instant.now());
        simulados.flush();
        simulados.manterRecentes(usuarioId, SIMULADOS_GUARDADOS);
        return doAluno(s);
    }

    @Transactional
    public void descartar(UUID usuarioId) {
        simulados.findByUsuarioIdAndFinalizadoEmIsNull(usuarioId).ifPresent(simulados::delete);
    }

    /** Simulados finalizados, do mais recente ao mais antigo, sem as questões. */
    @Transactional(readOnly = true)
    public List<ResumoDeSimulado> finalizados(UUID usuarioId) {
        return simulados.findByUsuarioIdAndFinalizadoEmIsNotNullOrderByFinalizadoEmDesc(usuarioId).stream()
                .map(s -> new ResumoDeSimulado(s.getId(), s.getCriadoEm(), s.getFinalizadoEm(), areasDe(s), s.getSegundos(),
                        Placar.de(questoesDe(s), respostasDe(s))))
                .toList();
    }

    /** Quando cada simulado foi finalizado: concluir um simulado conclui o bloco de simulado do dia. */
    @Transactional(readOnly = true)
    public List<Instant> finalizacoes(UUID usuarioId) {
        return simulados.finalizacoes(usuarioId);
    }

    @Transactional(readOnly = true)
    public SimuladoDoAluno buscar(UUID usuarioId, UUID id) {
        return simulados.findByIdAndUsuarioId(id, usuarioId).map(this::doAluno)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Simulado não encontrado."));
    }

    @EventListener
    public void aoZerar(AlunoService.DadosZerados evento) {
        simulados.deleteByUsuarioId(evento.usuarioId());
    }

    // -------------------------------------------------------------------------------------------

    private Simulado buscarEmAndamento(UUID usuarioId) {
        return simulados.findByUsuarioIdAndFinalizadoEmIsNull(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhum simulado em andamento."));
    }

    /** Em andamento vai sem gabarito e sem placar; finalizado vai com os dois. */
    private SimuladoDoAluno doAluno(Simulado s) {
        List<QuestaoEnem> questoes = questoesDe(s);
        Map<String, String> respostas = respostasDe(s);
        boolean aberto = s.emAndamento();
        return new SimuladoDoAluno(s.getId(), s.getCriadoEm(), s.getFinalizadoEm(), areasDe(s),
                aberto ? questoes.stream().map(QuestaoEnem::semGabarito).toList() : questoes,
                respostas, s.getSegundos(), aberto ? null : Placar.de(questoes, respostas));
    }

    private List<QuestaoEnem> questoesDe(Simulado s) {
        return json.readValue(s.getQuestoes(), new TypeReference<List<QuestaoEnem>>() { });
    }

    private Map<String, String> respostasDe(Simulado s) {
        return json.readValue(s.getRespostas(), new TypeReference<LinkedHashMap<String, String>>() { });
    }

    private List<String> areasDe(Simulado s) {
        return json.readValue(s.getAreas(), new TypeReference<List<String>>() { });
    }

    /** Formato do {@code Simulado} do app, mais o placar quando já foi finalizado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SimuladoDoAluno(UUID id, Instant createdAt, Instant finishedAt, List<String> areas,
            List<QuestaoEnem> questions, Map<String, String> answers, int seconds, Placar score) {
    }

    public record ResumoDeSimulado(UUID id, Instant createdAt, Instant finishedAt, List<String> areas, int seconds,
            Placar score) {
    }
}
