package com.focaai.api.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Faz o papel do Google nos testes: assina ID tokens com uma chave RSA própria e troca o
 * decodificador do Google por um que confia nela, mantendo as mesmas regras de produção.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GoogleDeTeste {

    public static final String CLIENT_ID = "focaai-teste.apps.googleusercontent.com";
    private static final KeyPair CHAVES = gerarChaves();

    @Bean
    @Primary
    @Qualifier("decodificadorGoogle")
    JwtDecoder decodificadorGoogleDeTeste() {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withPublicKey((RSAPublicKey) CHAVES.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(VerificadorGoogle.regras(List.of(CLIENT_ID))));
        return decodificador;
    }

    /** ID token como o Google emitiria para o app, com {@code alteracoes} sobrescrevendo claims. */
    public static String idToken(String sub, Map<String, Object> alteracoes) {
        Instant agora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .subject(sub)
                .audience(List.of(CLIENT_ID))
                .issuedAt(agora.minusSeconds(10))
                .expiresAt(agora.plusSeconds(3600))
                .claim("email", sub + "@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Aluno " + sub)
                .claim("picture", "https://lh3.googleusercontent.com/" + sub);
        alteracoes.forEach(claims::claim);
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) CHAVES.getPublic())
                .privateKey((RSAPrivateKey) CHAVES.getPrivate()).build();
        NimbusJwtEncoder codificador = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    public static String idToken(String sub) {
        return idToken(sub, Map.of());
    }

    private static KeyPair gerarChaves() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            return gerador.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
