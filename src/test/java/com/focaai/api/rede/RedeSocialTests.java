package com.focaai.api.rede;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.focaai.api.BancoDeTeste;
import com.focaai.api.ClienteDeTeste;
import com.focaai.api.ContaDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, ContaDeTeste.class, ClienteDeTeste.class})
class RedeSocialTests {

    @Autowired ClienteDeTeste api;
    @Autowired MockMvcTester mvc;

    // Sobrenome único: o banco é compartilhado por todos os testes da execução.
    String sobrenome;
    ContaDeTeste.Logado ana;
    ContaDeTeste.Logado bia;

    @BeforeEach
    void criarAlunos() {
        sobrenome = "Teste" + UUID.randomUUID().toString().substring(0, 8);
        ana = api.novoAluno("Ana " + sobrenome);
        bia = api.novoAluno("Bia " + sobrenome);
        api.como(ana);
    }

    String relacaoCom(ContaDeTeste.Logado outro) {
        JsonNode perfil = api.ler(api.get("/api/usuarios/" + outro.id()));
        return perfil.get("friendship").isNull() ? null : perfil.get("friendship").asString();
    }

    void ficarAmigas() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        api.como(bia);
        api.put("/api/eu/amigos/" + ana.id(), null);
        api.como(ana);
    }

    @Test
    void pedirAceitarEDesfazerAmizade() {
        assertThat(relacaoCom(bia)).isNull();
        assertThat(relacaoCom(ana)).isEqualTo("self");

        JsonNode pedido = api.ler(api.put("/api/eu/amigos/" + bia.id(), null));
        assertThat(pedido.get("friendship").asString()).isEqualTo("sent");
        // Pedir de novo não duplica.
        assertThat(api.put("/api/eu/amigos/" + bia.id(), null)).hasStatus(200);

        api.como(bia);
        assertThat(relacaoCom(ana)).isEqualTo("received");
        JsonNode amigos = api.ler(api.get("/api/eu/amigos"));
        assertThat(amigos.get("received").get(0).get("id").asString()).isEqualTo(ana.id().toString());
        assertThat(amigos.get("friends").size()).isZero();

        // Pedir de volta aceita o pedido.
        assertThat(api.ler(api.put("/api/eu/amigos/" + ana.id(), null)).get("friendship").asString()).isEqualTo("friends");
        JsonNode perfil = api.ler(api.get("/api/usuarios/" + ana.id()));
        assertThat(perfil.get("friends").asInt()).isEqualTo(1);
        assertThat(perfil.get("user").get("name").asString()).isEqualTo("Ana " + sobrenome);
        assertThat(perfil.get("user").has("email")).isFalse();

        api.como(ana);
        assertThat(api.ler(api.get("/api/eu/amigos")).get("friends").get(0).get("id").asString()).isEqualTo(bia.id().toString());
        assertThat(api.delete("/api/eu/amigos/" + bia.id())).hasStatus(204);
        assertThat(relacaoCom(bia)).isNull();
        api.como(bia);
        assertThat(relacaoCom(ana)).isNull();
    }

    @Test
    void recusarOuCancelarPedido() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        api.como(bia);
        assertThat(api.delete("/api/eu/amigos/" + ana.id())).hasStatus(204);
        api.como(ana);
        assertThat(relacaoCom(bia)).isNull();
    }

    @Test
    void pedidosInvalidos() {
        assertThat(api.put("/api/eu/amigos/" + ana.id(), null)).hasStatus(400);
        assertThat(api.put("/api/eu/amigos/" + UUID.randomUUID(), null)).hasStatus(404);
        assertThat(api.get("/api/usuarios/" + UUID.randomUUID())).hasStatus(404);
    }

    @Test
    void buscarPorNome() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        JsonNode achados = api.ler(api.get("/api/usuarios?busca=" + sobrenome.toLowerCase()));
        // Quem busca não aparece.
        assertThat(achados.size()).isEqualTo(1);
        assertThat(achados.get(0).get("user").get("name").asString()).isEqualTo("Bia " + sobrenome);
        assertThat(achados.get(0).get("friendship").asString()).isEqualTo("sent");
        assertThat(api.ler(api.get("/api/usuarios?busca=a")).size()).isZero();
        assertThat(api.ler(api.get("/api/usuarios?busca=%25")).size()).isZero();
    }

    Map<String, Object> edicao(String nome, String bio, String foto) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("name", nome);
        corpo.put("bio", bio);
        corpo.put("picture", foto);
        return corpo;
    }

    @Test
    void editarPerfil() {
        JsonNode perfil = api.ler(api.put("/api/eu/perfil-publico", edicao("  Ana Clara  ", "  Medicina na USP  ", null)));
        assertThat(perfil.get("user").get("name").asString()).isEqualTo("Ana Clara");
        assertThat(perfil.get("bio").asString()).isEqualTo("Medicina na USP");
        assertThat(perfil.get("joinedAt").asString()).isNotBlank();
        assertThat(api.put("/api/eu/perfil-publico", edicao("Ana", "x".repeat(161), null))).hasStatus(400);
        assertThat(api.put("/api/eu/perfil-publico", edicao("   ", null, null))).hasStatus(400);
        assertThat(api.put("/api/eu/perfil-publico", edicao("x".repeat(51), null, null))).hasStatus(400);
        assertThat(api.ler(api.put("/api/eu/perfil-publico", edicao("Ana", "", null))).get("bio").isNull()).isTrue();
    }

    // PNG de 1x1 pixel.
    static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    String enviarFoto() {
        return api.ler(api.post("/api/imagens", Map.of("data", PNG))).get("url").asString();
    }

    @Test
    void fotoNoPerfilENoPost() {
        String foto = enviarFoto();
        assertThat(foto).startsWith("/api/imagens/");
        // Abre sem token, para a tela carregar direto.
        assertThat(mvc.get().uri(foto)).hasStatusOk().hasContentType("image/png");
        assertThat(mvc.get().uri("/api/imagens/" + UUID.randomUUID())).hasStatus(404);

        JsonNode perfil = api.ler(api.put("/api/eu/perfil-publico", edicao("Ana", null, foto)));
        assertThat(perfil.get("user").get("picture").asString()).isEqualTo(foto);
        // Mandar a mesma foto de novo (só mudando o nome) continua valendo.
        assertThat(api.put("/api/eu/perfil-publico", edicao("Ana B.", null, foto))).hasStatusOk();

        JsonNode post = api.ler(api.post("/api/posts", Map.of("text", "", "image", foto)));
        assertThat(post.get("image").asString()).isEqualTo(foto);
        assertThat(post.get("text").asString()).isEmpty();
        assertThat(api.ler(api.post("/api/posts", Map.of("text", "Sem foto"))).has("image")).isFalse();

        // A foto de outra aluna não serve.
        api.como(bia);
        assertThat(api.put("/api/eu/perfil-publico", edicao("Bia", null, foto))).hasStatus(400);
        assertThat(api.post("/api/posts", Map.of("text", "Oi", "image", foto))).hasStatus(400);
        assertThat(api.put("/api/eu/perfil-publico", edicao("Bia", null, "https://site-qualquer.com/a.png"))).hasStatus(400);
    }

    @Test
    void imagemInvalida() {
        assertThat(api.post("/api/imagens", Map.of("data", "nao-e-base64!"))).hasStatus(400);
        assertThat(api.post("/api/imagens", Map.of("data", Base64.getEncoder().encodeToString("texto".getBytes()))))
                .hasStatus(400);
        String grande = Base64.getEncoder().encodeToString(new byte[ImagemService.MAX_BYTES + 1]);
        assertThat(api.post("/api/imagens", Map.of("data", grande))).hasStatus(400);
    }

    @Test
    void postsSoParaAmigos() {
        api.como(bia);
        JsonNode post = api.ler(api.post("/api/posts", Map.of("text", "Fechei 40 questões de matemática hoje!")));
        assertThat(post.get("mine").asBoolean()).isTrue();
        assertThat(post.get("comments").asInt()).isZero();
        assertThat(post.get("createdAt").asString()).isNotBlank();
        long id = post.get("id").asLong();

        // Ana ainda não é amiga: não vê o post nem consegue comentar.
        api.como(ana);
        assertThat(api.ler(api.get("/api/eu/feed")).get("posts").size()).isZero();
        assertThat(api.get("/api/usuarios/" + bia.id() + "/posts")).hasStatus(403);
        assertThat(api.get("/api/posts/" + id)).hasStatus(404);
        assertThat(api.post("/api/posts/" + id + "/comentarios", Map.of("text", "Oi"))).hasStatus(404);

        ficarAmigas();
        JsonNode feed = api.ler(api.get("/api/eu/feed"));
        assertThat(feed.get("posts").get(0).get("id").asLong()).isEqualTo(id);
        assertThat(feed.get("posts").get(0).get("mine").asBoolean()).isFalse();
        assertThat(feed.get("next").isNull()).isTrue();
        assertThat(api.ler(api.get("/api/usuarios/" + bia.id() + "/posts")).get("posts").size()).isEqualTo(1);
        assertThat(api.delete("/api/posts/" + id)).hasStatus(403);

        // Desfazer a amizade tira o acesso de novo.
        api.delete("/api/eu/amigos/" + bia.id());
        assertThat(api.ler(api.get("/api/eu/feed")).get("posts").size()).isZero();
    }

    @Test
    void comentarios() {
        ficarAmigas();
        api.como(bia);
        long post = api.ler(api.post("/api/posts", Map.of("text", "Alguém tem resumo de citologia?"))).get("id").asLong();

        api.como(ana);
        JsonNode comentario = api.ler(api.post("/api/posts/" + post + "/comentarios", Map.of("text", "Tenho! Te mando.")));
        assertThat(comentario.get("canDelete").asBoolean()).isTrue();
        assertThat(api.post("/api/posts/" + post + "/comentarios", Map.of("text", "   "))).hasStatus(400);

        api.como(bia);
        long meu = api.ler(api.post("/api/posts/" + post + "/comentarios", Map.of("text", "Valeu!"))).get("id").asLong();
        JsonNode aberto = api.ler(api.get("/api/posts/" + post));
        assertThat(aberto.get("post").get("comments").asInt()).isEqualTo(2);
        assertThat(aberto.get("comments").get(0).get("text").asString()).isEqualTo("Tenho! Te mando.");
        // A autora do post apaga qualquer comentário nele.
        assertThat(aberto.get("comments").get(0).get("canDelete").asBoolean()).isTrue();

        // Ana não apaga o comentário da Bia.
        api.como(ana);
        assertThat(api.ler(api.get("/api/posts/" + post)).get("comments").get(1).get("canDelete").asBoolean()).isFalse();
        assertThat(api.delete("/api/posts/" + post + "/comentarios/" + meu)).hasStatus(403);

        api.como(bia);
        assertThat(api.delete("/api/posts/" + post + "/comentarios/" + comentario.get("id").asLong())).hasStatus(204);
        assertThat(api.delete("/api/posts/" + post)).hasStatus(204);
        assertThat(api.get("/api/posts/" + post)).hasStatus(404);
    }

    @Test
    void feedPaginado() {
        for (int i = 1; i <= PostService.POR_PAGINA + 3; i++) api.post("/api/posts", Map.of("text", "Post " + i));
        JsonNode primeira = api.ler(api.get("/api/eu/feed"));
        assertThat(primeira.get("posts").size()).isEqualTo(PostService.POR_PAGINA);
        assertThat(primeira.get("posts").get(0).get("text").asString()).isEqualTo("Post " + (PostService.POR_PAGINA + 3));
        JsonNode segunda = api.ler(api.get("/api/eu/feed?antes=" + primeira.get("next").asLong()));
        assertThat(segunda.get("posts").size()).isEqualTo(3);
        assertThat(segunda.get("next").isNull()).isTrue();
        assertThat(api.ler(api.get("/api/usuarios/" + ana.id())).get("posts").asInt()).isEqualTo(PostService.POR_PAGINA + 3);
    }

    @Test
    void postInvalido() {
        assertThat(api.post("/api/posts", Map.of("text", ""))).hasStatus(400);
        assertThat(api.post("/api/posts", Map.of("text", "x".repeat(PostService.MAX_POST + 1)))).hasStatus(400);
    }
}
