package com.focaai.api.auth;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/** Id do aluno logado, tirado do token de acesso já validado pelo Spring Security. */
public final class UsuarioAtual {

    private UsuarioAtual() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
