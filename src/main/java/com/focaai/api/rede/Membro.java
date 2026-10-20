package com.focaai.api.rede;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** O que um aluno mostra aos outros na rede: nome e foto, nunca o e-mail. */
public record Membro(UUID id, String name, String picture) {

    /** Colunas {@code u_id}, {@code u_nome} e {@code u_foto} da consulta. */
    static Membro de(ResultSet rs) throws SQLException {
        String nome = rs.getString("u_nome");
        return new Membro(rs.getObject("u_id", UUID.class), nome == null || nome.isBlank() ? "Aluno FocaAI" : nome,
                rs.getString("u_foto"));
    }
}
