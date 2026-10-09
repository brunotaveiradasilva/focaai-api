package com.focaai.api.estatisticas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.focaai.api.BancoDeTeste;
import com.focaai.api.ClienteDeTeste;
import com.focaai.api.ContaDeTeste;
import com.focaai.api.simulado.EnemDeTeste;

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
class EvolucaoEPessoasTests {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired ClienteDeTeste api;

    @BeforeEach
    void entrar() {
        api.novoAlunoComPerfil();
    }

    void checkin(String materia, int minutos, int questoes, int acertos) {
        Map<String, Object> c = new HashMap<>();
        c.put("at", Instant.now().toString());
        c.put("activity", "exercicios");
        c.put("subject", materia);
        c.put("minutes", minutos);
        c.put("feeling", 2);
        c.put("questions", questoes);
        c.put("correct", acertos);
        api.post("/api/eu/checkins", c);
    }

    @Test
    void evolucaoJuntaCheckinsESimulados() {
        checkin("mat", 60, 10, 7);
        checkin("bio", 30, 0, 0);
        api.post("/api/eu/simulados", Map.of("areas", List.of("natureza"), "count", 4, "range", "todos"));
        JsonNode sim = api.ler(api.get("/api/eu/simulados/em-andamento"));
        sim.get("questions").forEach(q ->
                api.put("/api/eu/simulados/em-andamento/respostas/" + q.get("key").asString(), Map.of("letter", "A")));
        api.post("/api/eu/simulados/em-andamento/finalizar", Map.of());

        JsonNode e = api.ler(api.get("/api/eu/estatisticas?dias=7"));
        LocalDate hoje = LocalDate.now(SP);
        assertThat(e.get("today").asString()).isEqualTo(hoje.toString());
        assertThat(e.get("streak").asInt()).isEqualTo(1);
        assertThat(e.get("bestStreak").asInt()).isEqualTo(1);
        assertThat(e.get("minutesToday").asInt()).isEqualTo(90);
        assertThat(e.get("days").size()).isEqualTo(7);
        assertThat(e.get("days").get(6).get("minutes").asInt()).isEqualTo(90);
        assertThat(e.get("minutesBySubject").get("mat").asInt()).isEqualTo(60);
        assertThat(e.get("questions").get("total").asInt()).isEqualTo(14);
        assertThat(e.get("questions").get("correct").asInt()).isEqualTo(11);
        assertThat(e.get("areas").get(2).get("area").asString()).isEqualTo("natureza");
        assertThat(e.get("areas").get(2).get("pct").asDouble()).isEqualTo(1.0);
        assertThat(e.get("areas").get(0).get("pct").isNull()).isTrue();
        assertThat(e.get("simulados").asInt()).isEqualTo(1);
        assertThat(e.get("checkins").asInt()).isEqualTo(2);
        assertThat(api.ler(api.get("/api/eu/estatisticas?dias=30")).get("days").size()).isEqualTo(30);
        assertThat(api.get("/api/eu/estatisticas?dias=0")).hasStatus(400);
    }

    @Test
    void pessoasEGrupos() {
        JsonNode tela = api.ler(api.get("/api/eu/pessoas"));
        assertThat(tela.get("matches").size()).isEqualTo(10);
        JsonNode primeira = tela.get("matches").get(0);
        assertThat(primeira.get("connection").isNull()).isTrue();
        String pessoa = primeira.get("person").get("id").asString();

        assertThat(api.post("/api/eu/pessoas/" + pessoa + "/conectar", null)).hasStatus(204);
        assertThat(api.post("/api/eu/pessoas/p999/conectar", null)).hasStatus(404);

        String grupo = tela.get("groups").get(1).get("id").asString();
        assertThat(grupo).isEqualTo("redacao-2026");
        assertThat(api.put("/api/eu/grupos/" + grupo, null)).hasStatus(204);
        assertThat(api.put("/api/eu/grupos/grupo-que-nao-existe", null)).hasStatus(404);

        tela = api.ler(api.get("/api/eu/pessoas"));
        assertThat(tela.get("matches").get(0).get("connection").asString()).isEqualTo("pending");
        assertThat(tela.get("groups").get(1).get("joined").asBoolean()).isTrue();

        assertThat(api.delete("/api/eu/grupos/" + grupo)).hasStatus(204);
        assertThat(api.ler(api.get("/api/eu/pessoas")).get("groups").get(1).get("joined").asBoolean()).isFalse();
    }

    @Test
    void zerarApagaConexoesEGrupos() {
        api.post("/api/eu/pessoas/p1/conectar", null);
        api.put("/api/eu/grupos/redacao-2026", null);
        api.delete("/api/eu/dados");
        api.put("/api/eu/perfil", com.focaai.api.PerfilDeTeste.perfil());
        JsonNode tela = api.ler(api.get("/api/eu/pessoas"));
        tela.get("matches").forEach(m -> assertThat(m.get("connection").isNull()).isTrue());
        tela.get("groups").forEach(g -> assertThat(g.get("joined").asBoolean()).isFalse());
    }
}
