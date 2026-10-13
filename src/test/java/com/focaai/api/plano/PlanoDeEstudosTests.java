package com.focaai.api.plano;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.focaai.api.BancoDeTeste;
import com.focaai.api.ClienteDeTeste;
import com.focaai.api.ContaDeTeste;
import com.focaai.api.PerfilDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, ContaDeTeste.class, ClienteDeTeste.class})
class PlanoDeEstudosTests {

    // Uma quinta-feira; o aluno de teste estuda toda noite, 3h em dois blocos de 90 min.
    static final LocalDate QUINTA = LocalDate.of(2026, 10, 8);

    @Autowired ClienteDeTeste api;

    @BeforeEach
    void entrar() {
        api.novoAlunoComPerfil();
    }

    JsonNode semana(LocalDate dia) {
        var r = api.get("/api/eu/semana?data=" + dia);
        assertThat(r).hasStatusOk();
        return api.ler(r);
    }

    static List<JsonNode> doDia(JsonNode semana, LocalDate dia) {
        List<JsonNode> tarefas = new ArrayList<>();
        semana.get("tasks").forEach(t -> {
            if (t.get("date").asString().equals(dia.toString())) tarefas.add(t);
        });
        return tarefas;
    }

    @Test
    void semanaTemSeteDiasDeBlocosEOResumo() {
        JsonNode s = semana(QUINTA);
        assertThat(s.get("today").asString()).isEqualTo("2026-10-08");
        assertThat(s.get("tasks").size()).isEqualTo(14);
        assertThat(s.get("tasks").get(0).get("date").asString()).isEqualTo("2026-10-05");
        assertThat(s.get("summary").get("weeklyMinutes").asInt()).isEqualTo(1260);
        assertThat(s.get("summary").get("goalLabel").asString()).isEqualTo("ENEM 2026 · Outras engenharias");
        assertThat(s.get("summary").get("examDates").get(0).get("date").asString()).isEqualTo("2026-11-08");
        assertThat(s.get("overdue").size()).isZero();
    }

    @Test
    void oPlanoDeHojeFicaCongelado() {
        List<JsonNode> antes = doDia(semana(QUINTA), QUINTA);
        String materia = antes.stream().filter(t -> t.has("topicId")).findFirst().orElseThrow().get("subject").asString();
        // Terminar o assunto atual mudaria o plano vivo; o de hoje não muda mais.
        JsonNode roteiro = api.ler(api.get("/api/eu/roteiros/" + materia));
        api.put("/api/eu/progresso/" + roteiro.get("current").asString(), Map.of("done", true));
        assertThat(doDia(semana(QUINTA), QUINTA)).isEqualTo(antes);
    }

    @Test
    void checkinNaTarefaMarcaComoFeita() {
        JsonNode tarefa = doDia(semana(QUINTA), QUINTA).get(0);
        Map<String, Object> c = new HashMap<>();
        c.put("at", Instant.now().toString());
        c.put("taskId", tarefa.get("id").asString());
        c.put("activity", tarefa.get("activity").asString());
        c.put("subject", tarefa.has("subject") ? tarefa.get("subject").asString() : null);
        c.put("minutes", 60);
        c.put("feeling", 2);
        c.put("questions", 0);
        c.put("correct", 0);
        api.post("/api/eu/checkins", c);
        assertThat(semana(QUINTA).get("done")).extracting(JsonNode::asString).contains(tarefa.get("id").asString());
    }

    @Test
    void tarefasDeOntemNaoFeitasFicamAtrasadasAteDispensar() {
        semana(QUINTA);
        JsonNode sexta = semana(QUINTA.plusDays(1));
        assertThat(sexta.get("overdue").size()).isEqualTo(2);
        String id = sexta.get("overdue").get(0).get("id").asString();
        assertThat(id).startsWith("2026-10-08");

        assertThat(api.post("/api/eu/tarefas/" + id + "/dispensar", null)).hasStatus(204);
        assertThat(semana(QUINTA.plusDays(1)).get("overdue").size()).isEqualTo(1);
    }

    @Test
    void mudarOPerfilRefazOPlano() {
        semana(QUINTA);
        assertThat(semana(QUINTA.plusDays(1)).get("overdue").size()).isEqualTo(2);
        api.patch("/api/eu/perfil", Map.of("minutesPerDay", 90));
        JsonNode s = semana(QUINTA.plusDays(1));
        assertThat(s.get("overdue").size()).isZero();
        assertThat(doDia(s, QUINTA.plusDays(1))).hasSize(1);
    }

    @Test
    void roteiroMostraOProgressoDoAluno() {
        JsonNode r = api.ler(api.get("/api/eu/roteiros/mat"));
        // O perfil de teste domina Matemática: o primeiro grupo já começa feito.
        assertThat(r.get("topics").get(0).get("state").asString()).isEqualTo("done");
        assertThat(r.get("topics").get(0).get("done").asInt()).isEqualTo(3);
        assertThat(r.get("topics").get(0).get("title").asString()).isEqualTo("Operações e propriedades");
        assertThat(r.get("current").asString()).startsWith("mat.");
        assertThat(api.get("/api/eu/roteiros/xyz")).hasStatus(404);
    }

    @Test
    void previaNaoPrecisaDePerfilSalvo() {
        api.novoAluno();
        JsonNode p = api.ler(api.post("/api/plano/previa", Map.of("profile", PerfilDeTeste.perfil())));
        assertThat(p.get("summary").get("weeklyMinutes").asInt()).isEqualTo(1260);
        assertThat(p.get("tasks").size()).isEqualTo(14);
        assertThat(api.get("/api/eu/estado")).bodyJson().extractingPath("$.profile").isNull();
    }

    @Test
    void semPerfilASemanaDa404() {
        api.novoAluno();
        assertThat(api.get("/api/eu/semana")).hasStatus(404);
    }
}
