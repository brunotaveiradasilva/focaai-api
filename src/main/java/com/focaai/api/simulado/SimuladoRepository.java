package com.focaai.api.simulado;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SimuladoRepository extends JpaRepository<Simulado, UUID> {

    Optional<Simulado> findByUsuarioIdAndFinalizadoEmIsNull(UUID usuarioId);

    Optional<Simulado> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Simulado> findByUsuarioIdAndFinalizadoEmIsNotNullOrderByFinalizadoEmDesc(UUID usuarioId);

    @Query(value = "SELECT q->>'key' FROM simulado, jsonb_array_elements(questoes) q WHERE usuario_id = :usuarioId",
            nativeQuery = true)
    List<String> questoesVistas(UUID usuarioId);

    /** Mantém só os {@code manter} simulados finalizados mais recentes. */
    @Modifying
    @Query(value = """
            DELETE FROM simulado WHERE usuario_id = :usuarioId AND finalizado_em IS NOT NULL AND id NOT IN (
                SELECT id FROM simulado WHERE usuario_id = :usuarioId AND finalizado_em IS NOT NULL
                ORDER BY finalizado_em DESC LIMIT :manter)
            """, nativeQuery = true)
    void manterRecentes(UUID usuarioId, int manter);

    void deleteByUsuarioId(UUID usuarioId);
}
