package com.focaai.api.plano;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Planos congelados por dia e tarefas dispensadas. */
@Repository
public class PlanoRepository {

    static final int DIAS_GUARDADOS = 21;
    static final int DISPENSADAS_GUARDADAS = 200;

    private final JdbcClient banco;
    private final ObjectMapper json;

    public PlanoRepository(JdbcClient banco, ObjectMapper json) {
        this.banco = banco;
        this.json = json;
    }

    /** Planos congelados entre {@code de} e {@code ate}, inclusive, por dia. */
    public Map<LocalDate, List<Tarefa>> congelados(UUID usuarioId, LocalDate de, LocalDate ate) {
        Map<LocalDate, List<Tarefa>> planos = new LinkedHashMap<>();
        banco.sql("SELECT dia, tarefas::text AS tarefas FROM plano_dia WHERE usuario_id = ? AND dia BETWEEN ? AND ? ORDER BY dia")
                .params(usuarioId, de, ate)
                .query((rs, n) -> Map.entry(rs.getObject("dia", LocalDate.class), rs.getString("tarefas")))
                .list()
                .forEach(e -> planos.put(e.getKey(), json.readValue(e.getValue(), new TypeReference<List<Tarefa>>() { })));
        return planos;
    }

    /** Congela o plano de um dia se ainda não estiver congelado; descarta os dias mais antigos. */
    public void congelar(UUID usuarioId, LocalDate dia, List<Tarefa> tarefas) {
        banco.sql("INSERT INTO plano_dia (usuario_id, dia, tarefas) VALUES (?, ?, ?::jsonb) ON CONFLICT DO NOTHING")
                .params(usuarioId, dia, json.writeValueAsString(tarefas))
                .update();
        banco.sql("""
                DELETE FROM plano_dia WHERE usuario_id = ? AND dia NOT IN (
                    SELECT dia FROM plano_dia WHERE usuario_id = ? ORDER BY dia DESC LIMIT ?)
                """)
                .params(usuarioId, usuarioId, DIAS_GUARDADOS)
                .update();
    }

    public void descongelarTudo(UUID usuarioId) {
        banco.sql("DELETE FROM plano_dia WHERE usuario_id = ?").param(usuarioId).update();
    }

    public Set<String> dispensadas(UUID usuarioId) {
        return new HashSet<>(banco.sql("SELECT tarefa_id FROM tarefa_dispensada WHERE usuario_id = ?")
                .param(usuarioId).query(String.class).list());
    }

    public void dispensar(UUID usuarioId, String tarefaId) {
        banco.sql("INSERT INTO tarefa_dispensada (usuario_id, tarefa_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(usuarioId, tarefaId)
                .update();
        banco.sql("""
                DELETE FROM tarefa_dispensada WHERE usuario_id = ? AND tarefa_id NOT IN (
                    SELECT tarefa_id FROM tarefa_dispensada WHERE usuario_id = ? ORDER BY em DESC LIMIT ?)
                """)
                .params(usuarioId, usuarioId, DISPENSADAS_GUARDADAS)
                .update();
    }

    public void apagarDispensadas(UUID usuarioId) {
        banco.sql("DELETE FROM tarefa_dispensada WHERE usuario_id = ?").param(usuarioId).update();
    }
}
