package com.focaai.api.plano;

import java.time.LocalDate;
import java.time.ZoneId;

import com.focaai.api.aluno.Perfil;
import com.focaai.api.auth.UsuarioAtual;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plano de estudos. Datas são do calendário do aluno: o app manda o fuso dele em {@code X-Timezone}
 * (ex.: {@code America/Sao_Paulo}) e, se quiser, o dia em {@code ?data=}.
 */
@RestController
@RequestMapping("/api")
public class PlanoController {

    private final PlanoService plano;

    public PlanoController(PlanoService plano) {
        this.plano = plano;
    }

    /** A semana de estudos (tela Início): tarefas, o que já foi feito e o que ficou para trás. */
    @GetMapping("/eu/semana")
    public PlanoService.Semana semana(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Timezone", required = false) String fuso,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        ZoneId zona = Datas.fuso(fuso);
        return plano.semana(UsuarioAtual.id(jwt), data != null ? data : LocalDate.now(zona), zona);
    }

    /** "Deixar pra lá" numa tarefa atrasada. */
    @PostMapping("/eu/tarefas/{tarefaId}/dispensar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dispensar(@AuthenticationPrincipal Jwt jwt, @PathVariable String tarefaId) {
        plano.dispensar(UsuarioAtual.id(jwt), tarefaId);
    }

    /** Objetivo, carga semanal e provas do aluno. */
    @GetMapping("/eu/plano")
    public PlanoService.Resumo resumo(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Timezone", required = false) String fuso) {
        return plano.resumo(UsuarioAtual.id(jwt), LocalDate.now(Datas.fuso(fuso)));
    }

    /** Roadmap de uma matéria com o progresso do aluno (aba Estudar). */
    @GetMapping("/eu/roteiros/{materia}")
    public RoteiroDoAluno roteiro(@AuthenticationPrincipal Jwt jwt, @PathVariable String materia) {
        return plano.roteiro(UsuarioAtual.id(jwt), materia);
    }

    /** O plano que um perfil ainda não salvo teria, para o onboarding mostrar antes de terminar. */
    @PostMapping("/plano/previa")
    public PlanoService.Previa previa(@RequestHeader(value = "X-Timezone", required = false) String fuso,
            @Valid @RequestBody PedidoDePrevia corpo) {
        return plano.previa(corpo.profile(), LocalDate.now(Datas.fuso(fuso)));
    }

    public record PedidoDePrevia(@NotNull @Valid Perfil profile) {
    }
}
