package com.focaai.api.auth;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.stereotype.Component;

/**
 * Confere o ID token que o app recebe do Google: assinatura (chaves públicas do Google), validade,
 * emissor e se foi emitido para um dos client IDs do FocaAI (web, Android ou iOS). Sem essa última
 * checagem, um token emitido para qualquer outro app serviria para entrar aqui.
 */
@Component
public class VerificadorGoogle {

    static final Set<String> EMISSORES = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decodificador;

    public VerificadorGoogle(@Qualifier("decodificadorGoogle") JwtDecoder decodificador) {
        this.decodificador = decodificador;
    }

    public ContaGoogle verificar(String idToken) {
        Jwt jwt;
        try {
            jwt = decodificador.decode(idToken);
        } catch (JwtException e) {
            throw new LoginInvalidoException("Login com Google recusado: token inválido ou expirado.");
        }
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new LoginInvalidoException("Login com Google recusado: e-mail da conta não verificado.");
        }
        return new ContaGoogle(jwt.getSubject(), jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    /** Regras que todo ID token do Google tem que cumprir, além da assinatura. */
    static OAuth2TokenValidator<Jwt> regras(List<String> clientIds) {
        OAuth2TokenValidator<Jwt> emissor = jwt -> EMISSORES.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emissor não é o Google", null));
        OAuth2TokenValidator<Jwt> publico = jwt -> jwt.getAudience() != null
                && jwt.getAudience().stream().anyMatch(clientIds::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token de outro app", null));
        return JwtValidators.createDefaultWithValidators(emissor, publico);
    }

    /** O que a API guarda da conta Google. {@code sub} é o id estável da conta. */
    public record ContaGoogle(String sub, String email, String nome, String foto) {
    }
}
