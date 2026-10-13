package com.focaai.api.conteudo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Assunto do roadmap de uma matéria. {@code groupName} inclui a matéria da taxonomia quando ela não é
 * a da aba (ex.: "Filosofia · Ética"). O nó de reta final de cada matéria ({@code final}) é de
 * simulados, não de conteúdo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Topico(
        String id,
        String subject,
        String title,
        int lessons,
        String groupId,
        String groupName,
        String desc,
        @JsonProperty("final") boolean retaFinal) {
}
