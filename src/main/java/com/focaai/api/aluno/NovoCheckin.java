package com.focaai.api.aluno;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Check-in enviado pelo app. {@code id} é opcional: o app pode gerar o seu para reenviar sem duplicar
 * quando a conexão cai. {@code activity} é a atividade da tarefa (teoria, exercicios, redacao,
 * revisao ou simulado).
 */
public record NovoCheckin(
        UUID id,
        @NotNull Instant at,
        String taskId,
        String subject,
        String topicId,
        @NotNull String activity,
        @Min(0) @Max(16 * 60) int minutes,
        @Min(1) @Max(3) int feeling,
        @Min(0) @Max(500) int questions,
        @Min(0) @Max(500) int correct) {
}
