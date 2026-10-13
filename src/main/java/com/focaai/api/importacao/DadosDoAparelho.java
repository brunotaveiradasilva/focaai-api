package com.focaai.api.importacao;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.focaai.api.aluno.Perfil;
import com.focaai.api.plano.Tarefa;
import com.focaai.api.simulado.QuestaoEnem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * O estado que o app guardava só no aparelho ({@code focaai-state-v1}, versão 3 do store), enviado
 * uma vez no primeiro login para não perder nada. Os ids do app são timestamps, não UUIDs.
 */
public record DadosDoAparelho(
        @NotNull @Valid Perfil profile,
        Map<String, Integer> progress,
        Map<String, String> focus,
        Map<String, Double> boost,
        List<CheckinLocal> checkins,
        List<SimuladoLocal> simulados,
        SimuladoLocal activeSimulado,
        List<AjusteLocal> adjustments,
        Map<String, List<Tarefa>> plans,
        List<String> dismissed,
        Map<String, String> connections,
        List<String> groups) {

    public record CheckinLocal(@NotNull String id, @NotNull Instant at, String taskId, String subject, String topicId,
            int minutes, int feeling, int questions, int correct) {
    }

    public record SimuladoLocal(@NotNull String id, @NotNull Instant createdAt, Instant finishedAt, List<String> areas,
            List<QuestaoEnem> questions, Map<String, String> answers, int seconds) {
    }

    public record AjusteLocal(@NotNull Instant at, String subject, @NotNull String text) {
    }
}
