package com.focaai.api.conteudo;

import java.util.List;

/**
 * Curso que o aluno quer. A concorrência ({@code tier}) é por curso, não por universidade: o mesmo
 * curso é mais ou menos tão disputado em qualquer lugar, e é isso que muda o ritmo do plano.
 */
public record Curso(String id, String name, String tier, List<String> focus) {
}
