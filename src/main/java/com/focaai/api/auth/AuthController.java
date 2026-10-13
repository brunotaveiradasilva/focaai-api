package com.focaai.api.auth;

import com.focaai.api.comum.RecursoNaoEncontradoException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final UsuarioRepository usuarios;

    public AuthController(AuthService auth, UsuarioRepository usuarios) {
        this.auth = auth;
        this.usuarios = usuarios;
    }

    /** O app manda o ID token que recebeu do Google e ganha os tokens da API. */
    @PostMapping("/auth/google")
    public AuthService.Login entrarComGoogle(@Valid @RequestBody EntrarComGoogle corpo) {
        return auth.entrarComGoogle(corpo.idToken());
    }

    @PostMapping("/auth/renovar")
    public AuthService.Login renovar(@Valid @RequestBody ComRefreshToken corpo) {
        return auth.renovar(corpo.refreshToken());
    }

    @PostMapping("/auth/sair")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair(@Valid @RequestBody ComRefreshToken corpo) {
        auth.sair(corpo.refreshToken());
    }

    @GetMapping("/eu")
    public UsuarioResumo eu(@AuthenticationPrincipal Jwt jwt) {
        return usuarios.findById(UsuarioAtual.id(jwt)).map(UsuarioResumo::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }

    public record EntrarComGoogle(@NotBlank String idToken) {
    }

    public record ComRefreshToken(@NotBlank String refreshToken) {
    }
}
