package com.focaai.api.aluno;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.comum.RecursoNaoEncontradoException;
import com.focaai.api.comum.RequisicaoInvalidaException;
import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.conteudo.Materia;
import com.focaai.api.conteudo.Topico;
import com.focaai.api.conteudo.TopicoNoRoteiro;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Perfil, progresso e check-ins de cada aluno. Porte das ações do store do app. */
@Service
public class AlunoService {

    static final double REFORCO_MAXIMO = 3;
    static final double REFORCO_DECAI = 0.25;
    static final int AJUSTES_GUARDADOS = 20;

    private final AlunoRepository alunos;
    private final CheckinRepository checkins;
    private final AjusteRepository ajustes;
    private final Catalogo catalogo;
    private final Treinador treinador;
    private final ObjectMapper json;
    private final Validator validador;
    private final ApplicationEventPublisher eventos;

    public AlunoService(AlunoRepository alunos, CheckinRepository checkins, AjusteRepository ajustes, Catalogo catalogo,
            Treinador treinador, ObjectMapper json, Validator validador, ApplicationEventPublisher eventos) {
        this.alunos = alunos;
        this.checkins = checkins;
        this.ajustes = ajustes;
        this.catalogo = catalogo;
        this.treinador = treinador;
        this.json = json;
        this.validador = validador;
        this.eventos = eventos;
    }

    // -------------------------------------------------------------------------------------------
    // Leitura
    // -------------------------------------------------------------------------------------------

    /** Estado do aluno para as telas; {@code profile} nulo = ainda não fez o onboarding. */
    @Transactional(readOnly = true)
    public EstadoDoAluno estado(UUID usuarioId) {
        return alunos.findById(usuarioId).map(this::estadoDe).orElseGet(EstadoDoAluno::vazio);
    }

    /** Tudo que o plano e as estatísticas precisam do aluno, já convertido. */
    @Transactional(readOnly = true)
    public DadosDoAluno dados(UUID usuarioId) {
        Aluno aluno = alunos.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Faça o onboarding primeiro."));
        return new DadosDoAluno(perfilDe(aluno), progressoDe(aluno), focoDe(aluno), reforcoDe(aluno));
    }

    @Transactional(readOnly = true)
    public List<CheckinDoAluno> checkins(UUID usuarioId) {
        return checkins.findByUsuarioIdOrderByFeitoEm(usuarioId).stream().map(CheckinDoAluno::de).toList();
    }

    // -------------------------------------------------------------------------------------------
    // Perfil
    // -------------------------------------------------------------------------------------------

    /**
     * Fim do onboarding. Refazer o questionário só troca o perfil: histórico e progresso ficam. No
     * primeiro, as matérias que o aluno já domina começam com o primeiro grupo de assuntos feito.
     */
    @Transactional
    public EstadoDoAluno salvarPerfil(UUID usuarioId, Perfil perfil) {
        validar(perfil);
        Aluno aluno = alunos.buscarParaAlterar(usuarioId).orElse(null);
        if (aluno == null) {
            aluno = new Aluno(usuarioId);
            aluno.setProgresso(escrever(progressoInicial(perfil)));
        }
        aluno.setPerfil(escrever(perfil));
        alunos.save(aluno);
        eventos.publishEvent(new PerfilAlterado(usuarioId));
        return estadoDe(aluno);
    }

    /** Muda só os campos enviados (ex.: a rotina, os minutos por dia). */
    @Transactional
    public EstadoDoAluno atualizarPerfil(UUID usuarioId, JsonNode mudancas) {
        if (!mudancas.isObject()) throw new RequisicaoInvalidaException("Envie um objeto com os campos a mudar.");
        Aluno aluno = buscarParaAlterar(usuarioId);
        ObjectNode atual = (ObjectNode) json.readTree(aluno.getPerfil());
        mudancas.properties().forEach(campo -> atual.set(campo.getKey(), campo.getValue()));
        Perfil perfil = json.treeToValue(atual, Perfil.class);
        validar(perfil);
        aluno.setPerfil(escrever(perfil));
        eventos.publishEvent(new PerfilAlterado(usuarioId));
        return estadoDe(aluno);
    }

    private Map<String, Integer> progressoInicial(Perfil perfil) {
        Map<String, Integer> progresso = new LinkedHashMap<>();
        for (Materia m : catalogo.materias()) {
            if (perfil.nivel(m.id()) != 3) continue;
            List<TopicoNoRoteiro> roteiro = catalogo.roteiro(m.id(), perfil.provas());
            if (roteiro.isEmpty()) continue;
            String primeiroGrupo = roteiro.get(0).groupId();
            for (TopicoNoRoteiro t : roteiro) {
                if (t.groupId().equals(primeiroGrupo)) progresso.put(t.id(), t.lessons());
            }
        }
        return progresso;
    }

    // -------------------------------------------------------------------------------------------
    // Roadmap
    // -------------------------------------------------------------------------------------------

    @Transactional
    public EstadoDoAluno marcarAssunto(UUID usuarioId, String topicoId, boolean feito) {
        Topico topico = catalogo.topico(topicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Assunto não encontrado: " + topicoId));
        Aluno aluno = buscarParaAlterar(usuarioId);
        Map<String, Integer> progresso = progressoDe(aluno);
        progresso.put(topicoId, feito ? topico.lessons() : 0);
        aluno.setProgresso(escrever(progresso));
        return estadoDe(aluno);
    }

    /** Assunto que o aluno escolheu focar numa matéria; o plano segue ele. {@code null} tira o foco. */
    @Transactional
    public EstadoDoAluno definirFoco(UUID usuarioId, String materia, String topicoId) {
        catalogo.materia(materia);
        if (topicoId != null && catalogo.topico(topicoId).filter(t -> t.subject().equals(materia)).isEmpty()) {
            throw new RequisicaoInvalidaException("Esse assunto não é de " + materia + ".");
        }
        Aluno aluno = buscarParaAlterar(usuarioId);
        Map<String, String> foco = focoDe(aluno);
        if (topicoId == null) foco.remove(materia);
        else foco.put(materia, topicoId);
        aluno.setFoco(escrever(foco));
        return estadoDe(aluno);
    }

    // -------------------------------------------------------------------------------------------
    // Check-in
    // -------------------------------------------------------------------------------------------

    private static final Set<String> ATIVIDADES = Set.of("teoria", "exercicios", "redacao", "revisao", "simulado");

    /**
     * Registra o estudo e aplica o que o {@link Treinador} decidiu: aulas no assunto, peso da matéria
     * no plano e o ajuste explicado ao aluno. O reforço tem teto, para uma matéria não tomar a semana,
     * e uma sessão normal (sem ajuste) alivia um pouco, para o peso extra sumir quando o aluno se recupera.
     */
    @Transactional
    public ResultadoDoCheckin registrarCheckin(UUID usuarioId, NovoCheckin c) {
        if (!ATIVIDADES.contains(c.activity())) throw new RequisicaoInvalidaException("Atividade desconhecida: " + c.activity());
        if (c.correct() > c.questions()) throw new RequisicaoInvalidaException("Acertos maiores que o número de questões.");
        if (c.subject() != null) catalogo.materia(c.subject());
        UUID id = c.id() != null ? c.id() : UUID.randomUUID();
        if (checkins.existsById(id)) throw new RequisicaoInvalidaException("Check-in já registrado: " + id);

        Aluno aluno = buscarParaAlterar(usuarioId);
        Treinador.Avaliacao avaliacao = treinador.avaliar(c.activity(), c.subject(), c.topicId(), c.minutes(),
                c.feeling(), c.questions(), c.correct());

        Topico topico = c.topicId() == null ? null : catalogo.topico(c.topicId()).orElse(null);
        if (topico != null && avaliacao.aulas() > 0) {
            Map<String, Integer> progresso = progressoDe(aluno);
            progresso.put(topico.id(), Math.min(topico.lessons(), progresso.getOrDefault(topico.id(), 0) + avaliacao.aulas()));
            aluno.setProgresso(escrever(progresso));
        }

        Treinador.AjusteDoPlano ajuste = avaliacao.ajuste();
        double delta = ajuste != null && ajuste.reforco() != null ? ajuste.reforco()
                : c.minutes() >= Treinador.MINUTOS_MINIMOS ? -REFORCO_DECAI : 0;
        if (c.subject() != null && delta != 0) {
            Map<String, Double> reforco = reforcoDe(aluno);
            double novo = Math.min(REFORCO_MAXIMO, Math.max(0, reforco.getOrDefault(c.subject(), 0.0) + delta));
            reforco.put(c.subject(), novo);
            aluno.setReforco(escrever(reforco));
        }

        Checkin checkin = checkins.save(new Checkin(id, usuarioId, c.at(), c.taskId(), c.subject(), c.topicId(),
                c.minutes(), c.feeling(), c.questions(), c.correct()));
        AjusteDoAluno ajusteDoAluno = null;
        if (ajuste != null) {
            ajustes.save(new Ajuste(usuarioId, c.at(), c.subject(), ajuste.texto()));
            ajustes.manterRecentes(usuarioId, AJUSTES_GUARDADOS);
            ajusteDoAluno = new AjusteDoAluno(c.at(), c.subject(), ajuste.texto());
        }
        return new ResultadoDoCheckin(CheckinDoAluno.de(checkin), avaliacao.aulas(), ajusteDoAluno, estadoDe(aluno));
    }

    // -------------------------------------------------------------------------------------------
    // Zerar
    // -------------------------------------------------------------------------------------------

    /** Apaga o estudo (perfil, progresso, histórico) e mantém a conta. */
    @Transactional
    public void zerar(UUID usuarioId) {
        eventos.publishEvent(new DadosZerados(usuarioId));
        checkins.deleteByUsuarioId(usuarioId);
        ajustes.deleteByUsuarioId(usuarioId);
        alunos.deleteById(usuarioId);
    }

    // -------------------------------------------------------------------------------------------

    private Aluno buscarParaAlterar(UUID usuarioId) {
        return alunos.buscarParaAlterar(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Faça o onboarding primeiro."));
    }

    private EstadoDoAluno estadoDe(Aluno aluno) {
        List<AjusteDoAluno> recentes = ajustes.findTop20ByUsuarioIdOrderByFeitoEmDescIdDesc(aluno.getUsuarioId()).stream()
                .map(a -> new AjusteDoAluno(a.getFeitoEm(), a.getMateria(), a.getTexto()))
                .toList();
        return new EstadoDoAluno(perfilDe(aluno), progressoDe(aluno), focoDe(aluno), reforcoDe(aluno), recentes);
    }

    private Perfil perfilDe(Aluno aluno) {
        return json.readValue(aluno.getPerfil(), Perfil.class);
    }

    private Map<String, Integer> progressoDe(Aluno aluno) {
        return json.readValue(aluno.getProgresso(), new TypeReference<LinkedHashMap<String, Integer>>() { });
    }

    private Map<String, String> focoDe(Aluno aluno) {
        return json.readValue(aluno.getFoco(), new TypeReference<LinkedHashMap<String, String>>() { });
    }

    private Map<String, Double> reforcoDe(Aluno aluno) {
        return json.readValue(aluno.getReforco(), new TypeReference<LinkedHashMap<String, Double>>() { });
    }

    private String escrever(Object valor) {
        return json.writeValueAsString(valor);
    }

    private void validar(Perfil perfil) {
        Set<ConstraintViolation<Perfil>> erros = validador.validate(perfil);
        if (!erros.isEmpty()) {
            ConstraintViolation<Perfil> erro = erros.iterator().next();
            throw new RequisicaoInvalidaException("Perfil inválido: " + erro.getPropertyPath() + " " + erro.getMessage());
        }
        for (String materia : perfil.levels().keySet()) {
            if (!catalogo.materiaExiste(materia)) throw new RequisicaoInvalidaException("Matéria desconhecida: " + materia);
        }
    }

    /** O perfil mudou: planos congelados e o que mais depender dele precisam ser refeitos. */
    public record PerfilAlterado(UUID usuarioId) {
    }

    /** O aluno zerou o estudo: quem guarda dados dele (simulados, planos...) apaga também. */
    public record DadosZerados(UUID usuarioId) {
    }

    public record DadosDoAluno(Perfil perfil, Map<String, Integer> progresso, Map<String, String> foco,
            Map<String, Double> reforco) {
    }

    /** {@code adjustment} nulo = o check-in não mudou o plano. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ResultadoDoCheckin(CheckinDoAluno checkin, int lessons, AjusteDoAluno adjustment, EstadoDoAluno state) {
    }

    public record AjusteDoAluno(Instant at, String subject, String text) {
    }
}
