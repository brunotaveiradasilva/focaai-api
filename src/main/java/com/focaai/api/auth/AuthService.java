package com.focaai.api.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrar com Google, renovar a sessão e sair. */
@Service
public class AuthService {

    private final VerificadorGoogle google;
    private final EmissorDeTokens tokens;
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;

    public AuthService(VerificadorGoogle google, EmissorDeTokens tokens, UsuarioRepository usuarios,
            SessaoRepository sessoes) {
        this.google = google;
        this.tokens = tokens;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    /** Primeiro login cria a conta; os seguintes só atualizam nome, e-mail e foto. */
    @Transactional
    public Login entrarComGoogle(String idToken) {
        VerificadorGoogle.ContaGoogle conta = google.verificar(idToken);
        Usuario usuario = usuarios.findByGoogleSub(conta.sub()).orElseGet(() -> new Usuario(conta.sub()));
        usuario.atualizarComGoogle(conta.email(), conta.nome(), conta.foto());
        usuarios.save(usuario);
        return abrirSessao(usuario);
    }

    /**
     * Troca um refresh token válido por um par novo e invalida o antigo: um refresh token roubado
     * serve no máximo uma vez, e quem usar primeiro derruba o outro.
     */
    @Transactional
    public Login renovar(String refreshToken) {
        Sessao sessao = sessoes.findByTokenHash(EmissorDeTokens.hash(refreshToken))
                .filter(s -> s.valida(Instant.now()))
                .orElseThrow(() -> new LoginInvalidoException("Sessão expirada. Entre de novo."));
        sessao.revogar();
        Usuario usuario = usuarios.findById(sessao.getUsuarioId())
                .orElseThrow(() -> new LoginInvalidoException("Conta não encontrada. Entre de novo."));
        return abrirSessao(usuario);
    }

    @Transactional
    public void sair(String refreshToken) {
        sessoes.findByTokenHash(EmissorDeTokens.hash(refreshToken)).ifPresent(Sessao::revogar);
    }

    /** O banco apaga em cascata tudo que é do usuário (sessões, perfil, check-ins...). */
    @Transactional
    public void excluirConta(UUID usuarioId) {
        usuarios.deleteById(usuarioId);
    }

    private Login abrirSessao(Usuario usuario) {
        String refresh = tokens.novoRefreshToken();
        sessoes.save(new Sessao(usuario.getId(), EmissorDeTokens.hash(refresh), tokens.expiracaoDoRefresh()));
        return new Login(tokens.acesso(usuario), refresh, tokens.segundosDeAcesso(), UsuarioResumo.de(usuario));
    }

    public record Login(String accessToken, String refreshToken, long expiresIn, UsuarioResumo user) {
    }
}
