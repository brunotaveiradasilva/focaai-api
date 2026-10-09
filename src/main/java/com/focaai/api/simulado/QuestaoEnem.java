package com.focaai.api.simulado;

import java.util.List;

/**
 * Uma questão real do ENEM, no formato do {@code SimQuestion} do app. {@code key} é
 * {@code ano-numero}; {@code correct} é a letra do gabarito.
 */
public record QuestaoEnem(
        String key,
        int year,
        int index,
        String area,
        String context,
        String intro,
        List<String> files,
        List<Alternativa> alternatives,
        String correct) {

    public record Alternativa(String letter, String text, String file) {
    }

    /** A mesma questão sem o gabarito, para o simulado em andamento. */
    public QuestaoEnem semGabarito() {
        return new QuestaoEnem(key, year, index, area, context, intro, files, alternatives, null);
    }
}
