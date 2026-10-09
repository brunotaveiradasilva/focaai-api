package com.focaai.api.auth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Tudo exige o token de acesso da API ({@code Authorization: Bearer ...}), menos o conteúdo fixo, o
 * login e o health check.
 */
@Configuration
public class SegurancaConfig {

    private static final Logger log = LoggerFactory.getLogger(SegurancaConfig.class);
    private static final String CHAVES_DO_GOOGLE = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, @Qualifier("decodificadorDaApi") JwtDecoder decodificadorDaApi) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/conteudo/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/google", "/api/auth/renovar", "/api/auth/sair").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decodificadorDaApi)))
                .build();
    }

    @Bean
    SecretKey chaveDosTokens(@Value("${app.auth.jwt-secret}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
        }
        if (segredo.startsWith("dev-")) {
            log.warn("JWT_SECRET de desenvolvimento em uso. Defina um JWT_SECRET forte antes de publicar a API.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder codificadorDaApi(SecretKey chave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    /** Aceita só tokens assinados por esta API, ainda dentro da validade. */
    @Bean
    JwtDecoder decodificadorDaApi(SecretKey chave) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(EmissorDeTokens.EMISSOR));
        return decodificador;
    }

    /** ID tokens do Google, conferidos contra as chaves públicas que o Google publica. */
    @Bean
    @Qualifier("decodificadorGoogle")
    JwtDecoder decodificadorGoogle(@Value("${app.auth.google.client-ids}") String clientIds) {
        List<String> ids = Arrays.stream(clientIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (ids.isEmpty()) {
            log.warn("GOOGLE_CLIENT_IDS vazio: nenhum login com Google vai ser aceito até ele ser configurado.");
        }
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(CHAVES_DO_GOOGLE).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(VerificadorGoogle.regras(ids)));
        return decodificador;
    }
}
