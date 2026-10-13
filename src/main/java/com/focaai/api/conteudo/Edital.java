package com.focaai.api.conteudo;

import java.util.List;

/**
 * Mapeamento do edital de uma prova para a árvore de conteúdos. Pesos: 1.0 = citado explicitamente,
 * 0.5 = citado em parte ou inferido. {@code reviewedBy} fica nulo até um professor revisar.
 */
public record Edital(
        String id,
        String exam,
        String source,
        String edital,
        String checkedAt,
        String reviewedBy,
        String notice,
        List<Obra> requiredBooks,
        List<TopicoDoEdital> topics) {

    public record Obra(String title, String author) {
    }

    /** {@code inTaxonomy} é falso quando o edital cita um assunto que a taxonomia ainda não tem. */
    public record TopicoDoEdital(String topicId, String name, double weight, boolean inTaxonomy) {
    }
}
