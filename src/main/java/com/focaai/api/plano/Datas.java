package com.focaai.api.plano;

import java.time.LocalDate;
import java.time.ZoneId;

/** Datas como o app conta: semana começando na segunda, dia 0 = segunda. */
public final class Datas {

    /** Fuso usado quando o app não informa o seu ({@code X-Timezone}). */
    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private Datas() {
    }

    public static int diaDaSemana(LocalDate d) {
        return d.getDayOfWeek().getValue() - 1;
    }

    public static LocalDate inicioDaSemana(LocalDate d) {
        return d.minusDays(diaDaSemana(d));
    }

    /** Fuso IANA mandado pelo app; inválido ou ausente vira o padrão. */
    public static ZoneId fuso(String nome) {
        if (nome == null || nome.isBlank()) return FUSO_PADRAO;
        try {
            return ZoneId.of(nome);
        } catch (RuntimeException e) {
            return FUSO_PADRAO;
        }
    }
}
