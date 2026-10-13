package com.focaai.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Código de 6 dígitos mandado por e-mail. Prova que quem está no app recebe os e-mails daquele
 * endereço. Vale por {@link #VALIDADE} e aguenta {@link #TENTATIVAS} erros; só o hash fica no banco.
 */
@Entity
@Table(name = "codigo_de_email")
public class CodigoDeEmail {

    static final Duration VALIDADE = Duration.ofMinutes(15);
    static final int TENTATIVAS = 5;
    /** Intervalo mínimo entre dois e-mails para o mesmo endereço, para ninguém usar a API para encher a caixa de alguém. */
    static final Duration ESPERA_PARA_REENVIAR = Duration.ofSeconds(60);

    public enum Finalidade { CADASTRO, SENHA }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Finalidade finalidade;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    /** Só no cadastro: o nome e a senha (em hash) esperam aqui até a conta ser criada. */
    private String nome;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    protected CodigoDeEmail() {
    }

    CodigoDeEmail(String email, Finalidade finalidade, String codigo, String nome, String senhaHash, Instant agora) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.finalidade = finalidade;
        this.codigoHash = hash(email, codigo);
        this.nome = nome;
        this.senhaHash = senhaHash;
        this.criadoEm = agora;
        this.expiraEm = agora.plus(VALIDADE);
    }

    boolean expirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    boolean recente(Instant agora) {
        return agora.isBefore(criadoEm.plus(ESPERA_PARA_REENVIAR));
    }

    /** Confere o código; cada erro conta uma tentativa. */
    boolean confere(String codigo) {
        boolean certo = MessageDigest.isEqual(
                codigoHash.getBytes(StandardCharsets.US_ASCII), hash(email, codigo).getBytes(StandardCharsets.US_ASCII));
        if (!certo) tentativas++;
        return certo;
    }

    boolean semTentativas() {
        return tentativas >= TENTATIVAS;
    }

    /** O e-mail entra no hash: o mesmo código em outro endereço dá outro hash. */
    private static String hash(String email, String codigo) {
        return EmissorDeTokens.hash(email + ":" + codigo);
    }

    String getNome() {
        return nome;
    }

    String getSenhaHash() {
        return senhaHash;
    }
}
