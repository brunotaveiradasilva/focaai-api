package com.focaai.api.importacao;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.focaai.api.aluno.Ajuste;
import com.focaai.api.aluno.AjusteRepository;
import com.focaai.api.aluno.Aluno;
import com.focaai.api.aluno.AlunoRepository;
import com.focaai.api.aluno.AlunoService;
import com.focaai.api.aluno.Checkin;
import com.focaai.api.aluno.CheckinRepository;
import com.focaai.api.aluno.EstadoDoAluno;
import com.focaai.api.comum.ConflitoException;
import com.focaai.api.comum.RequisicaoInvalidaException;
import com.focaai.api.plano.PlanoRepository;
import com.focaai.api.simulado.Simulado;
import com.focaai.api.simulado.SimuladoRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Leva para a conta o que o app tinha guardado só no aparelho. Só vale para conta sem perfil: com
 * dados na conta, a importação é recusada em vez de misturar dois históricos.
 */
@Service
public class ImportacaoService {

    private final AlunoRepository alunos;
    private final CheckinRepository checkins;
    private final AjusteRepository ajustes;
    private final SimuladoRepository simulados;
    private final PlanoRepository planos;
    private final AlunoService alunoService;
    private final JdbcClient banco;
    private final ObjectMapper json;
    private final Validator validador;

    public ImportacaoService(AlunoRepository alunos, CheckinRepository checkins, AjusteRepository ajustes,
            SimuladoRepository simulados, PlanoRepository planos, AlunoService alunoService, JdbcClient banco,
            ObjectMapper json, Validator validador) {
        this.alunos = alunos;
        this.checkins = checkins;
        this.ajustes = ajustes;
        this.simulados = simulados;
        this.planos = planos;
        this.alunoService = alunoService;
        this.banco = banco;
        this.json = json;
        this.validador = validador;
    }

    @Transactional
    public EstadoDoAluno importar(UUID usuarioId, DadosDoAparelho d) {
        validar(d);
        if (alunos.existsById(usuarioId)) {
            throw new ConflitoException("Esta conta já tem dados de estudo. A importação só vale para uma conta nova.");
        }

        Aluno aluno = new Aluno(usuarioId);
        aluno.setPerfil(json.writeValueAsString(d.profile()));
        aluno.setProgresso(json.writeValueAsString(ouVazio(d.progress())));
        aluno.setFoco(json.writeValueAsString(ouVazio(d.focus())));
        aluno.setReforco(json.writeValueAsString(ouVazio(d.boost())));
        alunos.save(aluno);

        for (DadosDoAparelho.CheckinLocal c : ouVazia(d.checkins())) {
            checkins.save(new Checkin(idDoApp(usuarioId, "checkin", c.id()), usuarioId, c.at(), c.taskId(), c.subject(),
                    c.topicId(), c.minutes(), c.feeling(), c.questions(), c.correct()));
        }

        List<DadosDoAparelho.AjusteLocal> recentes = new ArrayList<>(ouVazia(d.adjustments()));
        recentes.sort(Comparator.comparing(DadosDoAparelho.AjusteLocal::at).reversed());
        for (DadosDoAparelho.AjusteLocal a : recentes.subList(0, Math.min(20, recentes.size()))) {
            ajustes.save(new Ajuste(usuarioId, a.at(), a.subject(), a.text()));
        }

        List<DadosDoAparelho.SimuladoLocal> todos = new ArrayList<>(ouVazia(d.simulados()));
        if (d.activeSimulado() != null) todos.add(d.activeSimulado());
        for (DadosDoAparelho.SimuladoLocal s : todos) {
            Simulado novo = new Simulado(idDoApp(usuarioId, "simulado", s.id()), usuarioId, s.createdAt(),
                    json.writeValueAsString(ouVazia(s.areas())), json.writeValueAsString(ouVazia(s.questions())));
            novo.setRespostas(json.writeValueAsString(ouVazio(s.answers())));
            novo.setSegundos(s.seconds());
            // O ativo do app nunca tem finishedAt; um finalizado sem data entra como finalizado agora.
            if (s != d.activeSimulado()) novo.finalizar(Objects.requireNonNullElse(s.finishedAt(), s.createdAt()));
            simulados.save(novo);
        }

        ouVazio(d.plans()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> planos.congelar(usuarioId, LocalDate.parse(e.getKey()), e.getValue()));
        ouVazia(d.dismissed()).forEach(id -> planos.dispensar(usuarioId, id));

        ouVazio(d.connections()).forEach((pessoa, status) -> banco
                .sql("INSERT INTO conexao (usuario_id, pessoa_id, status) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .params(usuarioId, pessoa, status.equals("connected") ? "connected" : "pending").update());
        ouVazia(d.groups()).forEach(grupo -> banco
                .sql("INSERT INTO grupo_membro (usuario_id, grupo_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(usuarioId, grupo).update());

        alunos.flush();
        return alunoService.estado(usuarioId);
    }

    /** UUID estável a partir do id que o app usava, para reenviar não duplicar. */
    static UUID idDoApp(UUID usuarioId, String tipo, String idLocal) {
        return UUID.nameUUIDFromBytes((usuarioId + ":" + tipo + ":" + idLocal).getBytes(StandardCharsets.UTF_8));
    }

    private void validar(DadosDoAparelho d) {
        for (ConstraintViolation<DadosDoAparelho> erro : validador.validate(d)) {
            throw new RequisicaoInvalidaException("Dados do aparelho inválidos: " + erro.getPropertyPath() + " " + erro.getMessage());
        }
        if (d.plans() != null) {
            for (String dia : d.plans().keySet()) {
                try {
                    LocalDate.parse(dia);
                } catch (RuntimeException e) {
                    throw new RequisicaoInvalidaException("Dia de plano inválido: " + dia);
                }
            }
        }
    }

    private static <K, V> Map<K, V> ouVazio(Map<K, V> mapa) {
        return mapa == null ? Map.of() : mapa;
    }

    private static <T> List<T> ouVazia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }
}
