package com.focaai.api.auth;

import java.util.UUID;

/** O que o app mostra da conta: nome e foto do Google. */
public record UsuarioResumo(UUID id, String email, String name, String picture) {

    public static UsuarioResumo de(Usuario u) {
        return new UsuarioResumo(u.getId(), u.getEmail(), u.getNome(), u.getFoto());
    }
}
