package com.focaai.api.conteudo;

/** Quanto o plano aperta para cada faixa de concorrência do curso. */
public record Concorrencia(
        String label,
        String desc,
        int simuladosPerMonth,
        int redacoesPerWeek,
        double questionShare) {
}
