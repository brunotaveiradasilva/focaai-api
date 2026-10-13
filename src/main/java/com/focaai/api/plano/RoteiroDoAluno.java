package com.focaai.api.plano;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.focaai.api.conteudo.TopicoNoRoteiro;

/**
 * Roadmap de uma matéria como o aluno vê na aba Estudar: cada assunto com o estado ({@code done},
 * {@code doing}, {@code todo}), as aulas feitas e se é o foco; {@code current} é o assunto em que o
 * plano está.
 */
public record RoteiroDoAluno(String subject, String current, List<Assunto> topics) {

    public record Assunto(@JsonUnwrapped TopicoNoRoteiro topic, String state, int done, boolean focus) {
    }
}
