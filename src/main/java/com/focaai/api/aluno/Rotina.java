package com.focaai.api.aluno;

import java.util.Comparator;
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

    /** Tempo para se arrumar depois de acordar; nunca conta como estudo. */
    static final int SE_ARRUMAR = 30;
    /** Intervalos menores que isso entre compromissos não comportam um bloco de estudo. */
    static final int INTERVALO_MINIMO = 30;

    /** Dormir depois da meia-noite (ex.: 00:30) conta como o fim do mesmo dia. */
    private int horaDeDormir() {
        return sleep <= wake ? sleep + 24 * 60 : sleep;
    }

    /** Janelas de estudo por trás dos períodos do plano (manhã, tarde, noite), em minutos do dia. */
    public int[][] janelas() {
        int noite = Math.max(18 * 60, lunchEnd);
        return new int[][] {{wake + SE_ARRUMAR, lunchStart}, {lunchEnd, noite}, {noite, horaDeDormir()}};
    }

    /**
     * Minutos de uma janela que sobram entre os compromissos do dia (com deslocamento), contando só
     * os intervalos grandes o bastante para estudar.
     */
    public int minutosLivres(int dia, int periodo) {
        int de = janelas()[periodo][0];
        int ate = janelas()[periodo][1];
        if (ate <= de) return 0;
        List<int[]> ocupado = items.stream()
                .filter(i -> i.day() == dia)
                .map(i -> new int[] {i.start() - i.commute(), i.end() + i.commute()})
                .sorted(Comparator.comparingInt(o -> o[0]))
                .toList();
        int livre = 0;
        int cursor = de;
        for (int[] o : ocupado) {
            if (o[1] <= cursor) continue;
            if (o[0] >= ate) break;
            livre += intervalo(cursor, o[0], ate);
            cursor = Math.max(cursor, o[1]);
        }
        return livre + intervalo(cursor, ate, ate);
    }

    private static int intervalo(int cursor, int ate, int fimDaJanela) {
        int g = Math.min(ate, fimDaJanela) - cursor;
        return g >= INTERVALO_MINIMO ? g : 0;
    }

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
