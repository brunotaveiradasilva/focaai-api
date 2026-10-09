package com.focaai.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrar sem Google, só para rodar o app contra a API local antes de existirem client IDs OAuth.
 * Vive nos fontes de teste: nunca vai para o jar publicado. Ligado apenas pelo {@code FocaaiApiLocal}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(LoginDeDesenvolvimento.Endpoint.class)
public class LoginDeDesenvolvimento {

    @Bean
    @Order(0)
    SecurityFilterChain loginDeDesenvolvimentoAberto(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/auth/dev")
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .build();
    }

    @RestController
    public static class Endpoint {

        private final UsuarioRepository usuarios;
        private final AuthService auth;

        public Endpoint(UsuarioRepository usuarios, AuthService auth) {
            this.usuarios = usuarios;
            this.auth = auth;
        }

        /** Mesma resposta do login com Google, para a conta {@code dev:<email>}. */
        @PostMapping("/api/auth/dev")
        @Transactional
        public AuthService.Login entrar(@Valid @RequestBody Pedido pedido) {
            String sub = "dev:" + pedido.email();
            Usuario usuario = usuarios.findByGoogleSub(sub).orElseGet(() -> new Usuario(sub));
            usuario.atualizarComGoogle(pedido.email(), pedido.name(), null);
            usuarios.save(usuario);
            return auth.abrirSessao(usuario);
        }
    }

    public record Pedido(@NotBlank @Email String email, @NotBlank String name) {
    }
}
