package com.focaai.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    Optional<Sessao> findByTokenHash(String tokenHash);

    /** Derruba todos os aparelhos da conta (ex.: depois de trocar a senha). */
    @Modifying
    @Query("UPDATE Sessao s SET s.revogadaEm = :agora WHERE s.usuarioId = :usuarioId AND s.revogadaEm IS NULL")
    int revogarTodas(UUID usuarioId, Instant agora);
}
