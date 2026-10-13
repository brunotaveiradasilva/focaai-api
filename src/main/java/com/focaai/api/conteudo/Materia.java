package com.focaai.api.conteudo;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Matéria com aba própria no roadmap (mat, por, red, fis, qui, bio, his, geo). */
public record Materia(String id, String name, @JsonProperty("short") String sigla, String color) {
}
