package com.focaai.api;

import java.util.UUID;

import com.focaai.api.auth.EmissorDeTokens;
import com.focaai.api.auth.Usuario;
import com.focaai.api.auth.UsuarioRepository;

import org.springframework.boot.test.context.TestComponent;

/** Cria um aluno logado direto no banco, sem passar pelo Google, e devolve o header de acesso. */
@TestComponent
public class ContaDeTeste {

    private final UsuarioRepository usuarios;
    private final EmissorDeTokens tokens;

    public ContaDeTeste(UsuarioRepository usuarios, EmissorDeTokens tokens) {
        this.usuarios = usuarios;
        this.tokens = tokens;
    }

    public Logado criar() {
        Usuario u = new Usuario("teste-" + UUID.randomUUID());
        u.atualizarComGoogle(u.getGoogleSub() + "@gmail.com", "Aluno de teste", null);
        usuarios.save(u);
        return new Logado(u.getId(), "Bearer " + tokens.acesso(u));
    }

    public record Logado(UUID id, String authorization) {
    }
}
