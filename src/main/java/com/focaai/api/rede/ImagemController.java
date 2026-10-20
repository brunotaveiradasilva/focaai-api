package com.focaai.api.rede;

import java.time.Duration;
import java.util.UUID;

import com.focaai.api.auth.UsuarioAtual;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Envio e leitura de fotos. Ler não pede token, para a foto abrir direto num {@code <Image>}: o id é
 * aleatório e só chega a quem já pode ver o perfil ou o post. Uma imagem nunca muda, então fica em cache.
 */
@RestController
public class ImagemController {

    private final ImagemService imagens;

    public ImagemController(ImagemService imagens) {
        this.imagens = imagens;
    }

    /** {@code data}: a imagem em base64 (JPEG, PNG ou WebP, até 2 MB). */
    @PostMapping("/api/imagens")
    @ResponseStatus(HttpStatus.CREATED)
    public ImagemService.Enviada enviar(@AuthenticationPrincipal Jwt jwt, @RequestBody NovaImagem corpo) {
        return imagens.salvar(UsuarioAtual.id(jwt), corpo.data());
    }

    @GetMapping("/api/imagens/{id}")
    public ResponseEntity<byte[]> ler(@PathVariable UUID id) {
        return imagens.ler(id)
                .map(i -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(i.tipo()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(i.dados()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record NovaImagem(String data) {
    }
}
