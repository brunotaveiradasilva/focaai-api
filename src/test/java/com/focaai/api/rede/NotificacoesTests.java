package com.focaai.api.rede;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, ContaDeTeste.class, ClienteDeTeste.class})
class NotificacoesTests {

    @Autowired ClienteDeTeste api;
    @Autowired JdbcClient banco;

    ContaDeTeste.Logado ana;
    ContaDeTeste.Logado bia;

    @BeforeEach
    void criarAlunas() {
        ana = api.novoAluno("Ana");
        bia = api.novoAluno("Bia");
        api.como(ana);
    }

    JsonNode atividade() {
        return api.ler(api.get("/api/eu/notificacoes"));
    }

    void ficarAmigas() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        api.como(bia);
        api.put("/api/eu/amigos/" + ana.id(), null);
        api.como(ana);
    }

    @Test
    void pedidoEAceiteDeAmizade() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        // Pedir de novo não repete a notificação.
        api.put("/api/eu/amigos/" + bia.id(), null);

        api.como(bia);
        JsonNode dela = atividade();
        assertThat(dela.get("unread").asInt()).isEqualTo(1);
        assertThat(dela.get("items").size()).isEqualTo(1);
        JsonNode pedido = dela.get("items").get(0);
        assertThat(pedido.get("type").asString()).isEqualTo("friend_request");
        assertThat(pedido.get("actor").get("id").asString()).isEqualTo(ana.id().toString());
        assertThat(pedido.get("read").asBoolean()).isFalse();
        assertThat(api.ler(api.get("/api/eu/notificacoes/nao-lidas")).get("unread").asInt()).isEqualTo(1);

        // Aceitar tira o pedido da atividade de quem aceitou e avisa quem pediu.
        api.put("/api/eu/amigos/" + ana.id(), null);
        assertThat(atividade().get("items").size()).isZero();
        api.como(ana);
        JsonNode aceite = atividade().get("items").get(0);
        assertThat(aceite.get("type").asString()).isEqualTo("friend_accepted");
        assertThat(aceite.get("actor").get("name").asString()).isEqualTo("Bia");
    }

    @Test
    void recusarApagaOPedido() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        api.como(bia);
        api.delete("/api/eu/amigos/" + ana.id());
        assertThat(atividade().get("items").size()).isZero();
    }

    @Test
    void postDeAmigoEComentario() {
        ContaDeTeste.Logado estranha = api.novoAluno("Cris");
        api.como(ana);
        ficarAmigas();
        api.como(bia);
        atividade();
        api.post("/api/eu/notificacoes/lidas", null);

        long post = api.ler(api.post("/api/posts", Map.of("text", "Terminei a lista de genética!"))).get("id").asLong();
        api.como(ana);
        JsonNode aviso = atividade().get("items").get(0);
        assertThat(aviso.get("type").asString()).isEqualTo("post");
        assertThat(aviso.get("postId").asLong()).isEqualTo(post);
        assertThat(aviso.get("excerpt").asString()).isEqualTo("Terminei a lista de genética!");
        // Quem não é amigo não fica sabendo.
        api.como(estranha);
        assertThat(atividade().get("items").size()).isZero();

        api.como(ana);
        api.post("/api/posts/" + post + "/comentarios", Map.of("text", "Bora revisar juntas"));
        api.como(bia);
        // Comentar no próprio post não avisa a si mesma.
        api.post("/api/posts/" + post + "/comentarios", Map.of("text", "Valeu!"));
        JsonNode dela = atividade();
        assertThat(dela.get("unread").asInt()).isEqualTo(1);
        JsonNode comentario = dela.get("items").get(0);
        assertThat(comentario.get("type").asString()).isEqualTo("comment");
        assertThat(comentario.get("actor").get("name").asString()).isEqualTo("Ana");
        assertThat(comentario.get("excerpt").asString()).isEqualTo("Bora revisar juntas");

        // Apagar o post leva junto os avisos dele.
        api.delete("/api/posts/" + post);
        assertThat(atividade().get("items").size()).isZero();
    }

    @Test
    void marcarComoLidas() {
        api.put("/api/eu/amigos/" + bia.id(), null);
        api.como(bia);
        assertThat(api.post("/api/eu/notificacoes/lidas", null)).hasStatus(204);
        JsonNode dela = atividade();
        assertThat(dela.get("unread").asInt()).isZero();
        assertThat(dela.get("items").get(0).get("read").asBoolean()).isTrue();
    }

    void contaCriadaHaDias(UUID id, int dias) {
        banco.sql("UPDATE usuario SET criado_em = now() - make_interval(days => ?) WHERE id = ?").params(dias, id).update();
    }

    long lembretes() {
        JsonNode itens = atividade().get("items");
        long n = 0;
        for (JsonNode i : itens) if (i.get("type").asString().equals("simulado_reminder")) n++;
        return n;
    }

    @Test
    void lembreteDeSimulado() {
        // Conta nova ainda não recebe.
        assertThat(lembretes()).isZero();

        contaCriadaHaDias(ana.id(), 10);
        assertThat(lembretes()).isEqualTo(1);
        JsonNode lembrete = atividade().get("items").get(0);
        assertThat(lembrete.has("actor")).isFalse();
        // Um por semana: abrir de novo não cria outro.
        assertThat(api.ler(api.get("/api/eu/notificacoes/nao-lidas")).get("unread").asInt()).isEqualTo(1);
        assertThat(lembretes()).isEqualTo(1);

        // Quem fez simulado na semana não é lembrado.
        api.como(bia);
        contaCriadaHaDias(bia.id(), 10);
        banco.sql("INSERT INTO simulado (id, usuario_id, criado_em, areas, questoes) VALUES (?, ?, now() - interval '2 days', '[]', '[]')")
                .params(UUID.randomUUID(), bia.id()).update();
        assertThat(lembretes()).isZero();
    }
}
