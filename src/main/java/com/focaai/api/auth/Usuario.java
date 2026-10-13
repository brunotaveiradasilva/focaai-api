package com.focaai.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma conta. Entra com Google, com e-mail e senha, ou com os dois (mesmo e-mail, mesma conta). */
@Entity
@Table(name = "usuario")
public class Usuario {

    /** Senha errada tantas vezes seguidas bloqueia a conta por {@link #BLOQUEIO}. */
    static final int FALHAS_ATE_BLOQUEAR = 10;
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    private String nome;

    private String foto;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(name = "falhas_de_senha", nullable = false)
    private int falhasDeSenha;

    @Column(name = "bloqueada_ate")
    private Instant bloqueadaAte;

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

    /** Conta criada com e-mail e senha, depois de o código mandado para o e-mail ser confirmado. */
    static Usuario comSenha(String email, String nome, String senhaHash) {
        Usuario usuario = new Usuario(null);
        usuario.email = normalizarEmail(email);
        usuario.nome = nome;
        usuario.senhaHash = senhaHash;
        usuario.ultimoLoginEm = usuario.criadoEm;
        return usuario;
    }

    /** E-mails comparados sem diferenciar maiúsculas, como o índice único do banco. */
    static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Nome, e-mail e foto acompanham a conta Google a cada login. */
    public void atualizarComGoogle(String email, String nome, String foto) {
        this.email = email;
        this.nome = nome;
        this.foto = foto;
        this.ultimoLoginEm = Instant.now();
    }

    /** Conta de e-mail e senha que passa a entrar também com o Google do mesmo e-mail. */
    void vincularGoogle(String googleSub) {
        this.googleSub = googleSub;
    }

    /** Cria ou troca a senha. Quem provou ser dono do e-mail recomeça sem bloqueio. */
    void definirSenha(String senhaHash) {
        this.senhaHash = senhaHash;
        this.falhasDeSenha = 0;
        this.bloqueadaAte = null;
    }

    void completarNome(String nome) {
        if (this.nome == null || this.nome.isBlank()) this.nome = nome;
    }

    boolean temSenha() {
        return senhaHash != null;
    }

    boolean bloqueada(Instant agora) {
        return bloqueadaAte != null && agora.isBefore(bloqueadaAte);
    }

    void registrarSenhaErrada(Instant agora) {
        falhasDeSenha++;
        if (falhasDeSenha >= FALHAS_ATE_BLOQUEAR) {
            falhasDeSenha = 0;
            bloqueadaAte = agora.plus(BLOQUEIO);
        }
    }

    void registrarLoginComSenha(Instant agora) {
        falhasDeSenha = 0;
        bloqueadaAte = null;
        ultimoLoginEm = agora;
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

    String getSenhaHash() {
        return senhaHash;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
