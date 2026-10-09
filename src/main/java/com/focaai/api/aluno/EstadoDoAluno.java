package com.focaai.api.aluno;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Mesmos nomes do store do app: {@code progress} = aulas feitas por assunto, {@code boost} = reforço por matéria. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EstadoDoAluno(
        Perfil profile,
        Map<String, Integer> progress,
        Map<String, String> focus,
        Map<String, Double> boost,
        List<AlunoService.AjusteDoAluno> adjustments) {

    static EstadoDoAluno vazio() {
        return new EstadoDoAluno(null, Map.of(), Map.of(), Map.of(), List.of());
    }
}
