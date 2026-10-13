package com.focaai.api.plano;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.focaai.api.aluno.CheckinDoAluno;
import com.focaai.api.aluno.Treinador;

/** Quais tarefas do plano o aluno já fez. Porte do {@code doneTaskIds} do app. */
public final class Conclusoes {

    private Conclusoes() {
    }

    /**
     * Uma tarefa está feita quando tem check-in de verdade (5 min ou mais) apontando para ela. Finalizar
     * um simulado no Banco de Questões conclui o bloco de simulado daquele dia.
     */
    public static Set<String> feitas(List<CheckinDoAluno> checkins, List<Instant> simuladosFinalizados, List<Tarefa> tarefas,
            ZoneId fuso) {
        Set<String> ids = new HashSet<>();
        for (CheckinDoAluno c : checkins) {
            if (c.taskId() != null && c.minutes() >= Treinador.MINUTOS_MINIMOS) ids.add(c.taskId());
        }
        Set<String> diasComSimulado = new HashSet<>();
        for (Instant fim : simuladosFinalizados) diasComSimulado.add(LocalDate.ofInstant(fim, fuso).toString());
        for (Tarefa t : tarefas) {
            if (t.activity().equals("simulado") && diasComSimulado.contains(t.date())) ids.add(t.id());
        }
        return ids;
    }
}
