package com.focaai.api.aluno;

import java.util.List;

import com.focaai.api.auth.UsuarioAtual;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/** Dados de estudo do aluno logado. */
@RestController
@RequestMapping("/api/eu")
public class AlunoController {

    private final AlunoService alunos;

    public AlunoController(AlunoService alunos) {
        this.alunos = alunos;
    }

    @GetMapping("/estado")
    public EstadoDoAluno estado(@AuthenticationPrincipal Jwt jwt) {
        return alunos.estado(UsuarioAtual.id(jwt));
    }

    /** Fim do onboarding (ou refazer o questionário). */
    @PutMapping("/perfil")
    public EstadoDoAluno salvarPerfil(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Perfil perfil) {
        return alunos.salvarPerfil(UsuarioAtual.id(jwt), perfil);
    }

    /** Muda só os campos enviados do perfil. */
    @PatchMapping("/perfil")
    public EstadoDoAluno atualizarPerfil(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode mudancas) {
        return alunos.atualizarPerfil(UsuarioAtual.id(jwt), mudancas);
    }

    @PutMapping("/progresso/{topicoId}")
    public EstadoDoAluno marcarAssunto(@AuthenticationPrincipal Jwt jwt, @PathVariable String topicoId,
            @RequestBody MarcarAssunto corpo) {
        return alunos.marcarAssunto(UsuarioAtual.id(jwt), topicoId, corpo.done());
    }

    @PutMapping("/foco/{materia}")
    public EstadoDoAluno definirFoco(@AuthenticationPrincipal Jwt jwt, @PathVariable String materia,
            @RequestBody DefinirFoco corpo) {
        return alunos.definirFoco(UsuarioAtual.id(jwt), materia, corpo.topicId());
    }

    @DeleteMapping("/foco/{materia}")
    public EstadoDoAluno tirarFoco(@AuthenticationPrincipal Jwt jwt, @PathVariable String materia) {
        return alunos.definirFoco(UsuarioAtual.id(jwt), materia, null);
    }

    @GetMapping("/checkins")
    public List<CheckinDoAluno> checkins(@AuthenticationPrincipal Jwt jwt) {
        return alunos.checkins(UsuarioAtual.id(jwt));
    }

    @PostMapping("/checkins")
    @ResponseStatus(HttpStatus.CREATED)
    public AlunoService.ResultadoDoCheckin registrarCheckin(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NovoCheckin corpo) {
        return alunos.registrarCheckin(UsuarioAtual.id(jwt), corpo);
    }

    /** "Começar do zero": apaga o estudo e mantém a conta. */
    @DeleteMapping("/dados")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zerar(@AuthenticationPrincipal Jwt jwt) {
        alunos.zerar(UsuarioAtual.id(jwt));
    }

    public record MarcarAssunto(boolean done) {
    }

    public record DefinirFoco(String topicId) {
    }
}
