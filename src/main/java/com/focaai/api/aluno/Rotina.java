package com.focaai.api.aluno;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A semana fixa do aluno (escola, trabalho, esporte...). Horários em minutos desde a meia-noite;
 * dias como no plano (0 = segunda).
 */
public record Rotina(
        @Min(0) @Max(24 * 60) int wake,
        @Min(0) @Max(24 * 60) int sleep,
        @Min(0) @Max(24 * 60) int lunchStart,
        @Min(0) @Max(24 * 60) int lunchEnd,
        @NotNull List<@Valid Item> items) {

    /** Um compromisso; {@code commute} é o tempo de deslocamento antes e depois, em minutos. */
    public record Item(
            @NotBlank String id,
            @NotBlank String kind,
            String name,
            @Min(0) @Max(6) int day,
            @Min(0) @Max(24 * 60) int start,
            @Min(0) @Max(24 * 60) int end,
            @Min(0) @Max(240) int commute) {
    }
}
