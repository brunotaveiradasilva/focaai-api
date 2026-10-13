package com.focaai.api.pessoas;

import com.focaai.api.auth.UsuarioAtual;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eu")
public class PessoasController {

    private final PessoasService pessoas;

    public PessoasController(PessoasService pessoas) {
        this.pessoas = pessoas;
    }

    @GetMapping("/pessoas")
    public PessoasService.TelaPessoas tela(@AuthenticationPrincipal Jwt jwt) {
        return pessoas.tela(UsuarioAtual.id(jwt));
    }

    @PostMapping("/pessoas/{pessoaId}/conectar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void conectar(@AuthenticationPrincipal Jwt jwt, @PathVariable String pessoaId) {
        pessoas.conectar(UsuarioAtual.id(jwt), pessoaId);
    }

    @PutMapping("/grupos/{grupoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void entrarNoGrupo(@AuthenticationPrincipal Jwt jwt, @PathVariable String grupoId) {
        pessoas.entrarNoGrupo(UsuarioAtual.id(jwt), grupoId);
    }

    @DeleteMapping("/grupos/{grupoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sairDoGrupo(@AuthenticationPrincipal Jwt jwt, @PathVariable String grupoId) {
        pessoas.sairDoGrupo(UsuarioAtual.id(jwt), grupoId);
    }
}
