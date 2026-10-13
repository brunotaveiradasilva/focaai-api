package com.focaai.api.auth;

import com.focaai.api.comum.RecursoNaoEncontradoException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final LoginComEmail porEmail;
    private final UsuarioRepository usuarios;

    public AuthController(AuthService auth, LoginComEmail porEmail, UsuarioRepository usuarios) {
        this.auth = auth;
        this.porEmail = porEmail;
        this.usuarios = usuarios;
    }

    /** O app manda o ID token que recebeu do Google e ganha os tokens da API. */
    @PostMapping("/auth/google")
    public AuthService.Login entrarComGoogle(@Valid @RequestBody EntrarComGoogle corpo) {
        return auth.entrarComGoogle(corpo.idToken());
    }

    /** Primeiro passo do cadastro: manda o código para o e-mail. A conta só nasce na confirmação. */
    @PostMapping("/auth/cadastro")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pedirCadastro(@Valid @RequestBody PedirCadastro corpo) {
        porEmail.pedirCadastro(corpo.email(), corpo.name(), corpo.password());
    }

    @PostMapping("/auth/cadastro/confirmar")
    public AuthService.Login confirmarCadastro(@Valid @RequestBody ConfirmarCodigo corpo) {
        return porEmail.confirmarCadastro(corpo.email(), corpo.code());
    }

    @PostMapping("/auth/entrar")
    public AuthService.Login entrarComSenha(@Valid @RequestBody EntrarComSenha corpo) {
        return porEmail.entrar(corpo.email(), corpo.password());
    }

    /** Sempre 204, tenha conta ou não: não revela quem está cadastrado. */
    @PostMapping("/auth/senha/esqueci")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esqueciASenha(@Valid @RequestBody SoEmail corpo) {
        porEmail.pedirNovaSenha(corpo.email());
    }

    @PostMapping("/auth/senha/redefinir")
    public AuthService.Login redefinirSenha(@Valid @RequestBody RedefinirSenha corpo) {
        return porEmail.redefinirSenha(corpo.email(), corpo.code(), corpo.password());
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

    /** Exclui a conta e todos os dados dela. Exigido pelas lojas de apps para quem cria conta. */
    @DeleteMapping("/eu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@AuthenticationPrincipal Jwt jwt) {
        auth.excluirConta(UsuarioAtual.id(jwt));
    }

    public record EntrarComGoogle(@NotBlank String idToken) {
    }

    public record PedirCadastro(
            @NotBlank @Email(message = "Confira o e-mail.") @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.") String password) {
    }

    public record ConfirmarCodigo(
            @NotBlank @Email(message = "Confira o e-mail.") String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}", message = "O código tem 6 números.") String code) {
    }

    public record EntrarComSenha(@NotBlank @Email(message = "Confira o e-mail.") String email, @NotBlank @Size(max = 200) String password) {
    }

    public record SoEmail(@NotBlank @Email(message = "Confira o e-mail.") String email) {
    }

    public record RedefinirSenha(
            @NotBlank @Email(message = "Confira o e-mail.") String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}", message = "O código tem 6 números.") String code,
            @NotBlank @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.") String password) {
    }

    public record ComRefreshToken(@NotBlank String refreshToken) {
    }
}
