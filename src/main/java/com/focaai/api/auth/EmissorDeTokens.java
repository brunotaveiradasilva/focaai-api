package com.focaai.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Tokens da própria API. O de acesso é um JWT curto (HS256) que vai em toda chamada; o refresh token
 * é um valor aleatório longo, guardado no aparelho, que troca por um par novo quando o de acesso vence.
 */
@Component
public class EmissorDeTokens {

    static final String EMISSOR = "focaai-api";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JwtEncoder codificador;
    private final Duration validadeAcesso;
    private final Duration validadeRenovacao;

    public EmissorDeTokens(
            JwtEncoder codificador,
            @Value("${app.auth.acesso-minutos:60}") long acessoMinutos,
            @Value("${app.auth.renovacao-dias:60}") long renovacaoDias) {
        this.codificador = codificador;
        this.validadeAcesso = Duration.ofMinutes(acessoMinutos);
        this.validadeRenovacao = Duration.ofDays(renovacaoDias);
    }

    public String acesso(Usuario usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(validadeAcesso))
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }

    public long segundosDeAcesso() {
        return validadeAcesso.toSeconds();
    }

    public String novoRefreshToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Instant expiracaoDoRefresh() {
        return Instant.now().plus(validadeRenovacao);
    }

    static String hash(String refreshToken) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
