package com.focaai.api.conteudo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Um assunto como um aluno vê: {@code weight} 1 = citado explicitamente por um dos editais que ele
 * segue, 0.5 = citado em parte (ou o edital da prova ainda não foi mapeado). {@code exams} detalha o
 * peso em cada prova, para a tela explicar de onde ele veio.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopicoNoRoteiro(
        String id,
        String subject,
        String title,
        int lessons,
        String groupId,
        String groupName,
        String desc,
        @JsonProperty("final") boolean retaFinal,
        double weight,
        List<PesoNaProva> exams) {

    /** Assunto secundário: citado só em parte pelos editais. */
    public boolean secundario() {
        return !retaFinal && weight < 1;
    }

    /** {@code mapped} = o edital dessa prova já foi mapeado para a árvore de conteúdos. */
    public record PesoNaProva(String examId, boolean mapped, double weight) {
    }
}
