package com.focaai.api.rede;

import java.util.List;
import java.util.UUID;

import com.focaai.api.auth.UsuarioAtual;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rede entre alunos: perfis, amizades, posts e comentários. */
@RestController
public class RedeController {

    private final RedeService rede;
    private final PostService posts;

    public RedeController(RedeService rede, PostService posts) {
        this.rede = rede;
        this.posts = posts;
    }

    // Perfis e amizades

    @GetMapping("/api/usuarios")
    public List<RedeService.MembroComRelacao> buscar(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String busca) {
        return rede.buscar(UsuarioAtual.id(jwt), busca);
    }

    @GetMapping("/api/usuarios/{id}")
    public RedeService.PerfilPublico perfil(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return rede.perfil(UsuarioAtual.id(jwt), id);
    }

    @GetMapping("/api/usuarios/{id}/posts")
    public PostService.Pagina postsDoAluno(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestParam(required = false) Long antes) {
        return posts.doAutor(UsuarioAtual.id(jwt), id, antes);
    }

    /** Nome, bio e foto do perfil. */
    @PutMapping("/api/eu/perfil-publico")
    public RedeService.PerfilPublico editarPerfil(@AuthenticationPrincipal Jwt jwt, @RequestBody EdicaoDoPerfil corpo) {
        return rede.editarPerfil(UsuarioAtual.id(jwt), corpo.name(), corpo.bio(), corpo.picture());
    }

    @GetMapping("/api/eu/amigos")
    public RedeService.Amigos amigos(@AuthenticationPrincipal Jwt jwt) {
        return rede.amigos(UsuarioAtual.id(jwt));
    }

    /** Pede amizade, ou aceita se a outra pessoa já tinha pedido. */
    @PutMapping("/api/eu/amigos/{id}")
    public RedeService.Relacao pedirAmizade(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return rede.pedirAmizade(UsuarioAtual.id(jwt), id);
    }

    /** Desfaz a amizade, cancela o pedido enviado ou recusa o recebido. */
    @DeleteMapping("/api/eu/amigos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desfazerAmizade(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        rede.desfazerAmizade(UsuarioAtual.id(jwt), id);
    }

    // Posts e comentários

    @GetMapping("/api/eu/feed")
    public PostService.Pagina feed(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long antes) {
        return posts.feed(UsuarioAtual.id(jwt), antes);
    }

    @PostMapping("/api/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostService.Post publicar(@AuthenticationPrincipal Jwt jwt, @RequestBody NovoPost corpo) {
        return posts.publicar(UsuarioAtual.id(jwt), corpo.text(), corpo.image());
    }

    @GetMapping("/api/posts/{id}")
    public PostService.PostAberto abrir(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return posts.abrir(UsuarioAtual.id(jwt), id);
    }

    @DeleteMapping("/api/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        posts.apagar(UsuarioAtual.id(jwt), id);
    }

    @PostMapping("/api/posts/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public PostService.Comentario comentar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Texto corpo) {
        return posts.comentar(UsuarioAtual.id(jwt), id, corpo.text());
    }

    @DeleteMapping("/api/posts/{id}/comentarios/{comentarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarComentario(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long comentarioId) {
        posts.apagarComentario(UsuarioAtual.id(jwt), id, comentarioId);
    }

    /** {@code picture}: a foto que já estava, uma enviada em {@code POST /api/imagens}, ou nula para tirar. */
    public record EdicaoDoPerfil(String name, String bio, String picture) {
    }

    /** {@code image}: uma foto enviada em {@code POST /api/imagens}, ou nula. */
    public record NovoPost(String text, String image) {
    }

    public record Texto(String text) {
    }
}
