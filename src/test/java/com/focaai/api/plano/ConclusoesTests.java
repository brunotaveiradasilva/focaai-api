package com.focaai.api.plano;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.focaai.api.aluno.CheckinDoAluno;

import org.junit.jupiter.api.Test;

class ConclusoesTests {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    static Tarefa tarefa(String id, String data, String atividade) {
        return new Tarefa(id, data, 0, 2, atividade, null, null, "x", 60, List.of());
    }

    static CheckinDoAluno checkin(String tarefa, int minutos) {
        return new CheckinDoAluno(UUID.randomUUID(), Instant.now(), tarefa, "mat", null, minutos, 2, 0, 0);
    }

    @Test
    void checkinCurtoNaoConcluiATarefa() {
        var tarefas = List.of(tarefa("a", "2026-10-08", "teoria"), tarefa("b", "2026-10-08", "teoria"));
        assertThat(Conclusoes.feitas(List.of(checkin("a", 5), checkin("b", 4)), List.of(), tarefas, SP)).containsExactly("a");
    }

    @Test
    void simuladoFinalizadoNoDiaConcluiOBlocoDeSimuladoNoFusoDoAluno() {
        var tarefas = List.of(tarefa("s1", "2026-10-10", "simulado"), tarefa("s2", "2026-10-11", "simulado"),
                tarefa("t", "2026-10-10", "teoria"));
        // 01:30 UTC de domingo ainda é sábado em São Paulo.
        Instant sabadoANoite = Instant.parse("2026-10-11T01:30:00Z");
        assertThat(Conclusoes.feitas(List.of(), List.of(sabadoANoite), tarefas, SP)).containsExactly("s1");
        assertThat(Conclusoes.feitas(List.of(), List.of(sabadoANoite), tarefas, ZoneId.of("UTC"))).containsExactly("s2");
    }
}
