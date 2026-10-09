package com.focaai.api.simulado;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @param areas áreas do ENEM (linguagens, humanas, natureza, matematica), na ordem da prova
 * @param range {@code recentes} (2019 em diante) ou {@code todos} (2009 em diante)
 */
public record NovoSimulado(
        @NotNull List<String> areas,
        @Min(1) @Max(180) int count,
        @NotNull @Pattern(regexp = "recentes|todos") String range) {
}
