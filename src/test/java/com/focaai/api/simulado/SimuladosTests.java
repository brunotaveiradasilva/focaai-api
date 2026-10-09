package com.focaai.api.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.focaai.api.BancoDeTeste;
import com.focaai.api.ClienteDeTeste;
import com.focaai.api.ContaDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, ContaDeTeste.class, ClienteDeTeste.class, EnemDeTeste.class})
class SimuladosTests {

    static final Map<String, Object> QUATRO_AREAS = Map.of(
            "areas", List.of("linguagens", "humanas", "natureza", "matematica"), "count", 22, "range", "recentes");

    @Autowired ClienteDeTeste api;

    @BeforeEach
    void entrar() {
        api.novoAlunoComPerfil();
    }

    @Test
    void semOnboardingNaoMontaSimulado() {
        api.novoAluno();
        assertThat(api.post("/api/eu/simulados", QUATRO_AREAS)).hasStatus(404);
    }

    @Test
    void divideAsQuestoesPorAreaNaOrdemDaProvaESemGabarito() {
        JsonNode sim = api.ler(api.post("/api/eu/simulados", QUATRO_AREAS));
        List<String> areas = new ArrayList<>();
        sim.get("questions").forEach(q -> areas.add(q.get("area").asString()));
        // 22 em 4 áreas: as duas primeiras ficam com a sobra.
        assertThat(areas).containsExactly(
                "linguagens", "linguagens", "linguagens", "linguagens", "linguagens", "linguagens",
                "humanas", "humanas", "humanas", "humanas", "humanas", "humanas",
                "natureza", "natureza", "natureza", "natureza", "natureza",
                "matematica", "matematica", "matematica", "matematica", "matematica");
        sim.get("questions").forEach(q -> {
            assertThat(q.has("correct")).isFalse();
            assertThat(q.get("year").asInt()).isBetween(2019, 2023);
        });
        assertThat(sim.has("score")).isFalse();
        assertThat(api.ler(api.get("/api/eu/simulados/em-andamento")).get("id")).isEqualTo(sim.get("id"));
    }

    @Test
    void responderFinalizarEVerOResultado() {
        JsonNode sim = api.ler(api.post("/api/eu/simulados", Map.of("areas", List.of("matematica"), "count", 4, "range", "todos")));
        JsonNode questoes = sim.get("questions");
        api.put("/api/eu/simulados/em-andamento/respostas/" + questoes.get(0).get("key").asString(), Map.of("letter", "A"));
        api.put("/api/eu/simulados/em-andamento/respostas/" + questoes.get(1).get("key").asString(), Map.of("letter", "B"));
        assertThat(api.put("/api/eu/simulados/em-andamento/tempo", Map.of("seconds", 300))).hasStatus(204);

        JsonNode fim = api.ler(api.post("/api/eu/simulados/em-andamento/finalizar", Map.of()));
        assertThat(fim.get("finishedAt").isNull()).isFalse();
        assertThat(fim.get("seconds").asInt()).isEqualTo(300);
        assertThat(fim.get("questions").get(0).get("correct").asString()).isEqualTo("A");
        assertThat(fim.get("score").get("total").asInt()).isEqualTo(4);
        assertThat(fim.get("score").get("correct").asInt()).isEqualTo(1);
        assertThat(fim.get("score").get("byArea").get("matematica").get("correct").asInt()).isEqualTo(1);

        assertThat(api.get("/api/eu/simulados/em-andamento")).hasStatus(204);
        JsonNode lista = api.ler(api.get("/api/eu/simulados"));
        assertThat(lista.size()).isEqualTo(1);
        assertThat(lista.get(0).has("questions")).isFalse();
        assertThat(lista.get(0).get("score").get("correct").asInt()).isEqualTo(1);
        assertThat(api.ler(api.get("/api/eu/simulados/" + fim.get("id").asString())).get("answers").size()).isEqualTo(2);
    }

    @Test
    void respostaInvalida() {
        JsonNode sim = api.ler(api.post("/api/eu/simulados", Map.of("areas", List.of("matematica"), "count", 2, "range", "todos")));
        String chave = sim.get("questions").get(0).get("key").asString();
        assertThat(api.put("/api/eu/simulados/em-andamento/respostas/" + chave, Map.of("letter", "E"))).hasStatus(400);
        assertThat(api.put("/api/eu/simulados/em-andamento/respostas/1999-1", Map.of("letter", "A"))).hasStatus(404);
    }

    @Test
    void naoRepeteQuestaoEnquantoTiverInedita() {
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            JsonNode sim = api.ler(api.post("/api/eu/simulados", Map.of("areas", List.of("natureza"), "count", 10, "range", "recentes")));
            sim.get("questions").forEach(q -> assertThat(vistas.add(q.get("key").asString())).isTrue());
            api.post("/api/eu/simulados/em-andamento/finalizar", Map.of());
        }
    }

    @Test
    void novoSimuladoDescartaOQueEstavaEmAndamento() {
        JsonNode primeiro = api.ler(api.post("/api/eu/simulados", Map.of("areas", List.of("humanas"), "count", 3, "range", "todos")));
        JsonNode segundo = api.ler(api.post("/api/eu/simulados", Map.of("areas", List.of("humanas"), "count", 3, "range", "todos")));
        assertThat(api.get("/api/eu/simulados/" + primeiro.get("id").asString())).hasStatus(404);
        assertThat(api.ler(api.get("/api/eu/simulados/em-andamento")).get("id")).isEqualTo(segundo.get("id"));
        assertThat(api.delete("/api/eu/simulados/em-andamento")).hasStatus(204);
        assertThat(api.get("/api/eu/simulados/em-andamento")).hasStatus(204);
    }

    @Test
    void areaInvalidaDa400() {
        assertThat(api.post("/api/eu/simulados", Map.of("areas", List.of("astrologia"), "count", 3, "range", "todos"))).hasStatus(400);
        assertThat(api.post("/api/eu/simulados", Map.of("areas", List.of(), "count", 3, "range", "todos"))).hasStatus(400);
    }

    @Test
    void zerarApagaOsSimulados() {
        api.post("/api/eu/simulados", Map.of("areas", List.of("matematica"), "count", 2, "range", "todos"));
        api.post("/api/eu/simulados/em-andamento/finalizar", Map.of());
        api.delete("/api/eu/dados");
        assertThat(api.ler(api.get("/api/eu/simulados")).size()).isZero();
    }

    @Test
    void outroAlunoNaoVeOSimulado() {
        JsonNode sim = api.ler(api.post("/api/eu/simulados", Map.of("areas", List.of("matematica"), "count", 2, "range", "todos")));
        api.novoAlunoComPerfil();
        assertThat(api.get("/api/eu/simulados/" + sim.get("id").asString())).hasStatus(404);
    }
}
