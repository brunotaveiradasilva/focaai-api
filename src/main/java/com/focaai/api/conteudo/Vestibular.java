package com.focaai.api.conteudo;

import java.util.List;

/**
 * Vestibular que o aluno pode acompanhar; as datas vêm de {@link Prova} no ciclo escolhido.
 * {@code courses} são os ids de {@link Curso} que ele oferece, tirados dos documentos oficiais da
 * edição 2027 (edital, guia de carreiras ou manual do candidato).
 */
public record Vestibular(String id, String name, List<String> courses) {
}
