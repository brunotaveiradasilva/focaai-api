package com.focaai.api.aluno;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AjusteRepository extends JpaRepository<Ajuste, Long> {

    List<Ajuste> findTop20ByUsuarioIdOrderByFeitoEmDescIdDesc(UUID usuarioId);

    /** Mantém só os {@code manter} ajustes mais recentes. */
    @Modifying
    @Query(value = """
            DELETE FROM ajuste WHERE usuario_id = :usuarioId AND id NOT IN (
                SELECT id FROM ajuste WHERE usuario_id = :usuarioId ORDER BY feito_em DESC, id DESC LIMIT :manter)
            """, nativeQuery = true)
    void manterRecentes(UUID usuarioId, int manter);

    void deleteByUsuarioId(UUID usuarioId);
}
