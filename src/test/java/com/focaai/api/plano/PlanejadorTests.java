package com.focaai.api.plano;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.focaai.api.aluno.Perfil;
import com.focaai.api.conteudo.Catalogo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * O plano da API tem que ser idêntico ao que o app montava. {@code referencia/plano.json} tem 300
 * cenários aleatórios (semente fixa) rodados no {@code planner.ts} e no {@code routine.ts} do app:
 * perfil, rotina, progresso, foco, reforço e data variando.
 */
class PlanejadorTests {

    static final ObjectMapper JSON = JsonMapper.builder().build();
    static Planejador planejador;
    static JsonNode casos;

    @BeforeAll
    static void carregar() throws IOException {
        planejador = new Planejador(new Catalogo(JSON, List.of("ufms-2027")));
        try (InputStream in = new ClassPathResource("referencia/plano.json").getInputStream()) {
            casos = JSON.readTree(in);
        }
    }

    @Test
    void semanaIgualADoApp() {
        int i = 0;
        for (JsonNode c : casos) {
            Cenario s = cenario(c);
            List<Tarefa> semana = planejador.semana(s.perfil(), s.progresso(), s.foco(), s.reforco(), s.data());
            assertThat(semJsonNulo(JSON.valueToTree(semana))).as("cenário %d (%s)", i, s.data()).isEqualTo(c.get("weekPlan"));
            i++;
        }
    }

    @Test
    void minutosDiasEProvasIguaisAoApp() {
        for (JsonNode c : casos) {
            Cenario s = cenario(c);
            assertThat(planejador.minutosPorSemana(s.perfil())).isEqualTo(c.get("weeklyMinutes").asInt());
            assertThat(arvore(planejador.diasDeEstudo(s.perfil()))).isEqualTo(c.get("studyDays"));
            assertThat(arvore(s.perfil().provas())).isEqualTo(c.get("exams"));
            assertThat(planejador.objetivo(s.perfil())).isEqualTo(c.get("goalLabel").asString());
            assertThat(planejador.provasSeguidas(s.perfil()).stream().map(p -> p.id()).toList())
                    .isEqualTo(lista(c.get("followed")));

            List<DiaDeProva> proximas = planejador.proximasProvas(s.perfil(), s.data());
            JsonNode esperadas = c.get("upcoming");
            assertThat(proximas).hasSize(esperadas.size());
            for (int k = 0; k < proximas.size(); k++) {
                assertThat(proximas.get(k).examId()).isEqualTo(esperadas.get(k).get("exam").asString());
                assertThat(proximas.get(k).phase()).isEqualTo(esperadas.get(k).get("phase").asString());
                assertThat(proximas.get(k).date().toString()).isEqualTo(esperadas.get(k).get("date").asString());
            }
        }
    }

    @Test
    void assuntoAtualEPesosIguaisAoApp() {
        for (JsonNode c : casos) {
            Cenario s = cenario(c);
            c.get("current").properties().forEach(e ->
                    assertThat(planejador.assuntoAtual(e.getKey(), s.progresso(), s.foco(), s.perfil().provas()).id())
                            .as(e.getKey()).isEqualTo(e.getValue().asString()));
            Map<String, Double> pesos = planejador.pesosDasMaterias(s.perfil(), s.reforco());
            c.get("weights").properties().forEach(e ->
                    assertThat(pesos.get(e.getKey())).as(e.getKey()).isEqualTo(e.getValue().asDouble()));
            assertThat(pesos.keySet()).containsExactlyElementsOf(c.get("weights").propertyNames());
        }
    }

    @Test
    void tempoLivreDaRotinaIgualAoApp() {
        for (JsonNode c : casos) {
            if (c.get("free").isNull()) continue;
            Cenario s = cenario(c);
            for (int dia = 0; dia < 7; dia++) {
                for (int p = 0; p < 3; p++) {
                    assertThat(s.perfil().routine().minutosLivres(dia, p))
                            .as("dia %d período %d", dia, p).isEqualTo(c.get("free").get(dia).get(p).asInt());
                }
            }
            assertThat(arvore(s.perfil().routine().janelas())).isEqualTo(c.get("windows"));
        }
    }

    record Cenario(Perfil perfil, Map<String, Integer> progresso, Map<String, String> foco, Map<String, Double> reforco,
            LocalDate data) {
    }

    static Cenario cenario(JsonNode c) {
        return new Cenario(
                JSON.treeToValue(c.get("profile"), Perfil.class),
                JSON.convertValue(c.get("progress"), new TypeReference<Map<String, Integer>>() { }),
                JSON.convertValue(c.get("focus"), new TypeReference<Map<String, String>>() { }),
                JSON.convertValue(c.get("boost"), new TypeReference<Map<String, Double>>() { }),
                LocalDate.parse(c.get("date").asString()));
    }

    /** O app não manda {@code subject}/{@code topicId} quando não há; aqui eles viriam como null. */
    static JsonNode semJsonNulo(JsonNode tarefas) {
        tarefas.forEach(t -> {
            var obj = (tools.jackson.databind.node.ObjectNode) t;
            List<String> nulos = new ArrayList<>();
            obj.properties().forEach(e -> {
                if (e.getValue().isNull()) nulos.add(e.getKey());
            });
            nulos.forEach(obj::remove);
        });
        return tarefas;
    }

    static JsonNode arvore(Object valor) {
        return JSON.valueToTree(valor);
    }

    static List<String> lista(JsonNode no) {
        List<String> l = new ArrayList<>();
        no.forEach(x -> l.add(x.asString()));
        return l;
    }
}
