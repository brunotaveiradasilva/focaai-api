package com.focaai.api.importacao;

import com.focaai.api.aluno.EstadoDoAluno;
import com.focaai.api.auth.UsuarioAtual;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImportacaoController {

    private final ImportacaoService importacao;

    public ImportacaoController(ImportacaoService importacao) {
        this.importacao = importacao;
    }

    /** Leva para a conta o que estava só no aparelho. 409 se a conta já tiver perfil. */
    @PostMapping("/api/eu/importar")
    public EstadoDoAluno importar(@AuthenticationPrincipal Jwt jwt, @RequestBody DadosDoAparelho dados) {
        return importacao.importar(UsuarioAtual.id(jwt), dados);
    }
}
