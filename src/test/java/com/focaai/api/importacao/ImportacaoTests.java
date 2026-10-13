package com.focaai.api.importacao;

import static org.assertj.core.api.Assertions.assertThat;

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
class ImportacaoTests {

    @Autowired ClienteDeTeste api;

    @BeforeEach
    void entrar() {
        api.novoAluno();
    }

    /** O store do app como ele fica no AsyncStorage depois de alguns dias de uso. */
    static Map<String, Object> doAparelho() {
        Map<String, Object> questao = Map.of("key", "2023-136", "year", 2023, "index", 136, "area", "matematica",
                "context", "", "intro", "Quanto?", "files", List.of(),
                "alternatives", List.of(Map.of("letter", "A", "text", "1"), Map.of("letter", "B", "text", "2")),
                "correct", "B");
        Map<String, Object> tarefa = new HashMap<>();
        tarefa.put("id", "2026-10-07-2-0");
        tarefa.put("date", "2026-10-07");
        tarefa.put("day", 2);
        tarefa.put("period", 2);
        tarefa.put("activity", "teoria");
        tarefa.put("subject", "fis");
        tarefa.put("topicId", "fis.mecanica.leis-de-newton");
        tarefa.put("title", "Leis de Newton");
        tarefa.put("minutes", 90);
        tarefa.put("steps", List.of(Map.of("label", "Aula", "minutes", 90)));

        Map<String, Object> d = new HashMap<>();
        d.put("profile", PerfilDeTeste.perfil());
        d.put("progress", Map.of("fis.mecanica.leis-de-newton", 2));
        d.put("focus", Map.of("fis", "fis.mecanica.leis-de-newton"));
        d.put("boost", Map.of("fis", 0.75));
        d.put("checkins", List.of(
                Map.of("id", "1791500000000", "at", "2026-10-07T23:00:00.000Z", "taskId", "2026-10-07-2-0", "subject", "fis",
                        "topicId", "fis.mecanica.leis-de-newton", "minutes", 60, "feeling", 2, "questions", 10, "correct", 7)));
        d.put("simulados", List.of(Map.of("id", "1791400000000", "createdAt", "2026-10-06T22:00:00.000Z",
                "finishedAt", "2026-10-06T23:00:00.000Z", "areas", List.of("matematica"), "questions", List.of(questao),
                "answers", Map.of("2023-136", "B"), "seconds", 180)));
        d.put("activeSimulado", Map.of("id", "1791550000000", "createdAt", "2026-10-08T22:00:00.000Z",
                "areas", List.of("matematica"), "questions", List.of(questao), "answers", Map.of(), "seconds", 30));
        d.put("adjustments", List.of(Map.of("at", "2026-10-07T23:00:00.000Z", "subject", "fis", "text", "Física ganhou espaço.")));
        d.put("plans", Map.of("2026-10-07", List.of(tarefa)));
        d.put("dismissed", List.of("2026-10-06-2-0"));
        d.put("connections", Map.of("p1", "pending"));
        d.put("groups", List.of("redacao-2026"));
        return d;
    }

    @Test
    void levaTudoDoAparelhoParaAConta() {
        JsonNode estado = api.ler(api.post("/api/eu/importar", doAparelho()));
        assertThat(estado.get("profile").get("name").asString()).isEqualTo("Teste");
        // O progresso vem do aparelho; não recalcula o adiantamento do onboarding.
        assertThat(estado.get("progress").size()).isEqualTo(1);
        assertThat(estado.get("focus").get("fis").asString()).isEqualTo("fis.mecanica.leis-de-newton");
        assertThat(estado.get("boost").get("fis").asDouble()).isEqualTo(0.75);
        assertThat(estado.get("adjustments").get(0).get("text").asString()).isEqualTo("Física ganhou espaço.");

        assertThat(api.ler(api.get("/api/eu/checkins")).get(0).get("taskId").asString()).isEqualTo("2026-10-07-2-0");
        JsonNode finalizados = api.ler(api.get("/api/eu/simulados"));
        assertThat(finalizados.size()).isEqualTo(1);
        assertThat(finalizados.get(0).get("score").get("correct").asInt()).isEqualTo(1);
        assertThat(api.ler(api.get("/api/eu/simulados/em-andamento")).get("seconds").asInt()).isEqualTo(30);

        // O plano congelado de quarta e o check-in nele: a tarefa aparece feita na semana.
        JsonNode semana = api.ler(api.get("/api/eu/semana?data=2026-10-08"));
        assertThat(semana.get("done")).extracting(JsonNode::asString).contains("2026-10-07-2-0");
        assertThat(semana.get("overdue").size()).isZero();

        JsonNode pessoas = api.ler(api.get("/api/eu/pessoas"));
        assertThat(pessoas.get("groups").get(1).get("joined").asBoolean()).isTrue();
    }

    @Test
    void naoImportaPorCimaDeUmaContaComDados() {
        assertThat(api.post("/api/eu/importar", doAparelho())).hasStatusOk();
        assertThat(api.post("/api/eu/importar", doAparelho())).hasStatus(409);

        api.novoAlunoComPerfil();
        assertThat(api.post("/api/eu/importar", doAparelho())).hasStatus(409);
    }

    @Test
    void semPerfilNaoImporta() {
        Map<String, Object> d = doAparelho();
        d.remove("profile");
        assertThat(api.post("/api/eu/importar", d)).hasStatus(400);
    }

    @Test
    void soOPerfilBasta() {
        assertThat(api.post("/api/eu/importar", Map.of("profile", PerfilDeTeste.perfil()))).hasStatusOk();
        assertThat(api.ler(api.get("/api/eu/checkins")).size()).isZero();
    }
}
