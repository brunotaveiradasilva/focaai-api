package com.focaai.api.rede;

import com.focaai.api.auth.UsuarioAtual;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A atividade do aluno (as notificações) e o número de novas, para o sininho. */
@RestController
@RequestMapping("/api/eu/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacoes;

    public NotificacaoController(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    /** As 50 mais recentes e quantas não foram lidas. */
    @GetMapping
    public NotificacaoService.Atividade atividade(@AuthenticationPrincipal Jwt jwt) {
        return notificacoes.atividade(UsuarioAtual.id(jwt));
    }

    @GetMapping("/nao-lidas")
    public NotificacaoService.NaoLidas naoLidas(@AuthenticationPrincipal Jwt jwt) {
        return notificacoes.naoLidasAgora(UsuarioAtual.id(jwt));
    }

    /** O aluno abriu a atividade: tudo que estava lá fica como lido. */
    @PostMapping("/lidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLidas(@AuthenticationPrincipal Jwt jwt) {
        notificacoes.marcarLidas(UsuarioAtual.id(jwt));
    }
}
