package com.focaai.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um aparelho logado. O refresh token em si nunca é guardado, só o hash dele. */
@Entity
@Table(name = "sessao")
public class Sessao {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogada_em")
    private Instant revogadaEm;

    protected Sessao() {
    }

    public Sessao(UUID usuarioId, String tokenHash, Instant expiraEm) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.criadaEm = Instant.now();
        this.expiraEm = expiraEm;
    }

    public boolean valida(Instant agora) {
        return revogadaEm == null && agora.isBefore(expiraEm);
    }

    public void revogar() {
        if (revogadaEm == null) revogadaEm = Instant.now();
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
