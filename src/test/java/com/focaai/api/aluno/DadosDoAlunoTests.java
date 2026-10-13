package com.focaai.api.aluno;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.focaai.api.BancoDeTeste;
import com.focaai.api.ContaDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, ContaDeTeste.class})
class DadosDoAlunoTests {

    static final String PRIMEIRO_DE_MAT = "mat.aritmetica-e-algebra-basica.operacoes-e-propriedades";

    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired ContaDeTeste contas;

    ContaDeTeste.Logado aluno;

    @BeforeEach
    void entrar() {
        aluno = contas.criar();
    }

    static Map<String, Object> perfil() {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Teste");
        p.put("track", "enem");
        p.put("vestibulares", List.of());
        p.put("cycle", 2026);
        p.put("courseId", "engenharias");
        p.put("stage", "em3");
        p.put("firstAttempt", true);
        p.put("language", "ingles");
        p.put("levels", Map.of("mat", 3, "por", 2, "red", 2, "fis", 1, "qui", 2, "bio", 2, "his", 2, "geo", 2));
        List<Boolean> semana = new ArrayList<>(Collections.nCopies(21, false));
        for (int dia = 0; dia < 7; dia++) semana.set(14 + dia, true);
        p.put("week", semana);
        p.put("minutesPerDay", 180);
        p.put("sessionMin", 90);
        p.put("routine", Map.of("wake", 390, "sleep", 1380, "lunchStart", 720, "lunchEnd", 780, "items", List.of()));
        p.put("obstacles", List.of("celular"));
        p.put("createdAt", "2026-10-09T12:00:00.000Z");
        return p;
    }

    @Test
    void antesDoOnboardingNaoTemPerfil() {
        assertThat(get("/api/eu/estado")).hasStatusOk().bodyJson().extractingPath("$.profile").isNull();
    }

    @Test
    void onboardingAdiantaOPrimeiroGrupoDasMateriasQueOAlunoDomina() {
        JsonNode estado = ler(put("/api/eu/perfil", perfil()));
        assertThat(estado.get("profile").get("name").asString()).isEqualTo("Teste");
        assertThat(estado.get("progress").get(PRIMEIRO_DE_MAT).asInt()).isEqualTo(3);
        // Física é nível 1: começa do zero.
        estado.get("progress").propertyNames().forEach(id -> assertThat(id).startsWith("mat."));
    }

    @Test
    void refazerOOnboardingTrocaSoOPerfil() {
        put("/api/eu/perfil", perfil());
        put("/api/eu/progresso/fis.mecanica.leis-de-newton", Map.of("done", true));
        Map<String, Object> outro = perfil();
        outro.put("name", "Outro nome");
        outro.put("levels", Map.of("mat", 1));
        JsonNode estado = ler(put("/api/eu/perfil", outro));
        assertThat(estado.get("profile").get("name").asString()).isEqualTo("Outro nome");
        assertThat(estado.get("progress").get("fis.mecanica.leis-de-newton").asInt()).isEqualTo(3);
        assertThat(estado.get("progress").get(PRIMEIRO_DE_MAT).asInt()).isEqualTo(3);
    }

    @Test
    void perfilInvalidoDa400() {
        Map<String, Object> p = perfil();
        p.put("week", List.of(true));
        assertThat(put("/api/eu/perfil", p)).hasStatus(400);
        p = perfil();
        p.put("levels", Map.of("xyz", 2));
        assertThat(put("/api/eu/perfil", p)).hasStatus(400);
    }

    @Test
    void patchMudaSoOsCamposEnviados() {
        put("/api/eu/perfil", perfil());
        JsonNode estado = ler(enviar(HttpMethod.PATCH, "/api/eu/perfil", Map.of("minutesPerDay", 120)));
        assertThat(estado.get("profile").get("minutesPerDay").asInt()).isEqualTo(120);
        assertThat(estado.get("profile").get("name").asString()).isEqualTo("Teste");
        assertThat(enviar(HttpMethod.PATCH, "/api/eu/perfil", Map.of("track", "nada"))).hasStatus(400);
    }

    @Test
    void marcaEDesmarcaAssunto() {
        put("/api/eu/perfil", perfil());
        String id = "fis.mecanica.leis-de-newton";
        assertThat(ler(put("/api/eu/progresso/" + id, Map.of("done", true))).get("progress").get(id).asInt()).isEqualTo(3);
        assertThat(ler(put("/api/eu/progresso/" + id, Map.of("done", false))).get("progress").get(id).asInt()).isZero();
        assertThat(put("/api/eu/progresso/nao.existe", Map.of("done", true))).hasStatus(404);
    }

    @Test
    void focoSoAceitaAssuntoDaMateria() {
        put("/api/eu/perfil", perfil());
        String id = "fis.mecanica.leis-de-newton";
        assertThat(ler(put("/api/eu/foco/fis", Map.of("topicId", id))).get("focus").get("fis").asString()).isEqualTo(id);
        assertThat(put("/api/eu/foco/mat", Map.of("topicId", id))).hasStatus(400);
        assertThat(ler(enviar(HttpMethod.DELETE, "/api/eu/foco/fis", null)).get("focus").has("fis")).isFalse();
    }

    @Test
    void checkinCreditaAulasEAliviaOReforco() {
        put("/api/eu/perfil", perfil());
        String id = "fis.mecanica.leis-de-newton";
        // 10 de 20 em Física: sem aula creditada, Física ganha reforço.
        JsonNode r = ler(post("/api/eu/checkins", checkin("exercicios", "fis", id, 45, 2, 20, 10)));
        assertThat(r.get("lessons").asInt()).isZero();
        assertThat(r.get("adjustment").get("text").asString()).startsWith("Você acertou 10 de 20");
        assertThat(r.get("state").get("boost").get("fis").asDouble()).isEqualTo(1.0);

        // Uma hora de teoria normal: duas aulas, e o reforço alivia 0,25.
        r = ler(post("/api/eu/checkins", checkin("teoria", "fis", id, 60, 2, 0, 0)));
        assertThat(r.get("lessons").asInt()).isEqualTo(2);
        assertThat(r.get("adjustment").isNull()).isTrue();
        assertThat(r.get("state").get("progress").get(id).asInt()).isEqualTo(2);
        assertThat(r.get("state").get("boost").get("fis").asDouble()).isEqualTo(0.75);
        assertThat(r.get("state").get("adjustments").size()).isEqualTo(1);

        // Mais duas aulas não passam do total do assunto.
        r = ler(post("/api/eu/checkins", checkin("teoria", "fis", id, 60, 2, 0, 0)));
        assertThat(r.get("state").get("progress").get(id).asInt()).isEqualTo(3);

        assertThat(ler(get("/api/eu/checkins")).size()).isEqualTo(3);
    }

    @Test
    void checkinComMesmoIdNaoDuplica() {
        put("/api/eu/perfil", perfil());
        Map<String, Object> c = checkin("teoria", "mat", null, 30, 2, 0, 0);
        c.put("id", UUID.randomUUID().toString());
        assertThat(post("/api/eu/checkins", c)).hasStatus(201);
        assertThat(post("/api/eu/checkins", c)).hasStatus(400);
    }

    @Test
    void checkinComMaisAcertosQueQuestoesDa400() {
        put("/api/eu/perfil", perfil());
        assertThat(post("/api/eu/checkins", checkin("teoria", "mat", null, 30, 2, 3, 5))).hasStatus(400);
    }

    @Test
    void cadaAlunoVeSoOsProprios() {
        put("/api/eu/perfil", perfil());
        post("/api/eu/checkins", checkin("teoria", "mat", null, 30, 2, 0, 0));
        aluno = contas.criar();
        assertThat(get("/api/eu/estado")).bodyJson().extractingPath("$.profile").isNull();
        assertThat(ler(get("/api/eu/checkins")).size()).isZero();
    }

    @Test
    void zerarApagaOEstudoEMantemAConta() {
        put("/api/eu/perfil", perfil());
        post("/api/eu/checkins", checkin("teoria", "mat", null, 30, 2, 0, 0));
        assertThat(enviar(HttpMethod.DELETE, "/api/eu/dados", null)).hasStatus(204);
        assertThat(get("/api/eu/estado")).bodyJson().extractingPath("$.profile").isNull();
        assertThat(ler(get("/api/eu/checkins")).size()).isZero();
        assertThat(get("/api/eu")).hasStatusOk();
    }

    @Test
    void excluirAContaApagaTudo() {
        put("/api/eu/perfil", perfil());
        post("/api/eu/checkins", checkin("teoria", "mat", null, 30, 2, 0, 0));
        assertThat(enviar(HttpMethod.DELETE, "/api/eu", null)).hasStatus(204);
        assertThat(get("/api/eu")).hasStatus(404);
        assertThat(get("/api/eu/estado")).bodyJson().extractingPath("$.profile").isNull();
    }

    static Map<String, Object> checkin(String atividade, String materia, String topico, int minutos, int sensacao,
            int questoes, int acertos) {
        Map<String, Object> c = new HashMap<>();
        c.put("at", Instant.now().toString());
        c.put("activity", atividade);
        c.put("subject", materia);
        c.put("topicId", topico);
        c.put("minutes", minutos);
        c.put("feeling", sensacao);
        c.put("questions", questoes);
        c.put("correct", acertos);
        return c;
    }

    private MvcTestResult get(String uri) {
        return enviar(HttpMethod.GET, uri, null);
    }

    private MvcTestResult put(String uri, Object corpo) {
        return enviar(HttpMethod.PUT, uri, corpo);
    }

    private MvcTestResult post(String uri, Object corpo) {
        return enviar(HttpMethod.POST, uri, corpo);
    }

    private MvcTestResult enviar(HttpMethod metodo, String uri, Object corpo) {
        var pedido = mvc.method(metodo).uri(uri).header("Authorization", aluno.authorization());
        if (corpo != null) pedido = pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        return pedido.exchange();
    }

    private JsonNode ler(MvcTestResult resultado) {
        try {
            return json.readTree(resultado.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
