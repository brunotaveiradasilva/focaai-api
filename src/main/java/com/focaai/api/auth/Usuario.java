package com.focaai.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private UUID id;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    private String nome;

    private String foto;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "ultimo_login_em", nullable = false)
    private Instant ultimoLoginEm;

    protected Usuario() {
    }

    public Usuario(String googleSub) {
        this.id = UUID.randomUUID();
        this.googleSub = googleSub;
        this.criadoEm = Instant.now();
    }

    /** Nome, e-mail e foto acompanham a conta Google a cada login. */
    public void atualizarComGoogle(String email, String nome, String foto) {
        this.email = email;
        this.nome = nome;
        this.foto = foto;
        this.ultimoLoginEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public String getFoto() {
        return foto;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
