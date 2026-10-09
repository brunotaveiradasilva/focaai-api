package com.focaai.api.conteudo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Área de conhecimento do ENEM; também agrupa as questões do banco. */
public record AreaEnem(String id, String name, @JsonProperty("short") String sigla, List<String> subjects) {
}
