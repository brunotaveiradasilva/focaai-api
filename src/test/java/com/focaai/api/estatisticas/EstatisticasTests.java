package com.focaai.api.estatisticas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.focaai.api.aluno.CheckinDoAluno;
import com.focaai.api.aluno.Perfil;
import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.pessoas.Pessoas;
import com.focaai.api.plano.Planejador;
import com.focaai.api.simulado.Placar;
import com.focaai.api.simulado.QuestaoEnem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estatísticas e combinação de pessoas iguais às do app. {@code referencia/estatisticas.json} tem 150
 * alunos aleatórios (check-ins em qualquer hora do dia, simulados) rodados no {@code stats.ts} e no
 * {@code people.ts}, no fuso registrado no arquivo.
 */
class EstatisticasTests {

    static final ObjectMapper JSON = JsonMapper.builder().build();
    static Catalogo catalogo;
    static Planejador planejador;
    static Pessoas pessoas;
    static ZoneId fuso;
    static JsonNode casos;

    @BeforeAll
    static void carregar() throws IOException {
        catalogo = new Catalogo(JSON);
        planejador = new Planejador(catalogo);
        pessoas = new Pessoas(catalogo, JSON);
        try (InputStream in = new ClassPathResource("referencia/estatisticas.json").getInputStream()) {
            JsonNode raiz = JSON.readTree(in);
            fuso = ZoneId.of(raiz.get("fuso").asString());
            casos = raiz.get("casos");
        }
    }

    @Test
    void numerosIguaisAosDoApp() {
        int i = 0;
        for (JsonNode c : casos) {
            Perfil perfil = JSON.treeToValue(c.get("profile"), Perfil.class);
            List<CheckinDoAluno> checkins = checkins(c.get("checkins"));
            List<Estatisticas.SimuladoFeito> feitos = simulados(c.get("simulados"));
            LocalDate hoje = LocalDate.parse(c.get("today").asString());
            Set<Integer> dias = planejador.diasDeEstudo(perfil);
            String caso = "caso " + i++;

            assertThat(Estatisticas.sequencia(dias, checkins, feitos, hoje, fuso)).as(caso).isEqualTo(c.get("streak").asInt());
            assertThat(Estatisticas.melhorSequencia(dias, checkins, feitos, fuso)).as(caso).isEqualTo(c.get("bestStreak").asInt());
            assertThat(Estatisticas.minutosNoDia(checkins, hoje, fuso)).as(caso).isEqualTo(c.get("minutesToday").asInt());
            assertThat(arvore(Estatisticas.ultimosDias(checkins, 7, hoje, fuso))).as(caso).isEqualTo(c.get("last7"));
            assertThat(arvore(Estatisticas.ultimosDias(checkins, 30, hoje, fuso))).as(caso).isEqualTo(c.get("last30"));
            assertThat(Estatisticas.minutosPorMateria(checkins, hoje.minusDays(6), fuso)).as(caso)
                    .isEqualTo(JSON.convertValue(c.get("bySubject7"), new TypeReference<Map<String, Integer>>() { }));
            assertThat(Estatisticas.minutosPorMateria(checkins, hoje.minusDays(29), fuso)).as(caso)
                    .isEqualTo(JSON.convertValue(c.get("bySubject30"), new TypeReference<Map<String, Integer>>() { }));
            assertThat(numeros(arvore(Estatisticas.questoes(checkins, feitos)))).as(caso).isEqualTo(numeros(c.get("questions")));
            assertThat(numeros(arvore(Estatisticas.acertoPorArea(catalogo.areas(), feitos)))).as(caso).isEqualTo(numeros(c.get("areas")));
        }
    }

    @Test
    void combinacaoDePessoasIgualADoApp() {
        for (JsonNode c : casos) {
            Perfil perfil = JSON.treeToValue(c.get("profile"), Perfil.class);
            List<Pessoas.Combinacao> combinacoes = pessoas.combinar(perfil);
            JsonNode esperadas = c.get("matches");
            assertThat(combinacoes).hasSize(esperadas.size());
            for (int k = 0; k < combinacoes.size(); k++) {
                Pessoas.Combinacao m = combinacoes.get(k);
                JsonNode e = esperadas.get(k);
                assertThat(m.person().id()).isEqualTo(e.get("id").asString());
                assertThat(m.score()).isEqualTo(e.get("score").asInt());
                assertThat(arvore(m.reasons())).isEqualTo(e.get("reasons"));
                assertThat(arvore(m.helpsWith())).isEqualTo(e.get("helps"));
            }
            assertThat(arvore(pessoas.grupos(perfil))).isEqualTo(c.get("groups"));
        }
    }

    static List<CheckinDoAluno> checkins(JsonNode lista) {
        List<CheckinDoAluno> checkins = new ArrayList<>();
        for (JsonNode c : lista) {
            checkins.add(new CheckinDoAluno(UUID.randomUUID(), Instant.parse(c.get("at").asString()), null,
                    c.has("subject") ? c.get("subject").asString() : null, null, c.get("minutes").asInt(),
                    c.get("feeling").asInt(), c.get("questions").asInt(), c.get("correct").asInt()));
        }
        return checkins;
    }

    static List<Estatisticas.SimuladoFeito> simulados(JsonNode lista) {
        List<Estatisticas.SimuladoFeito> feitos = new ArrayList<>();
        for (JsonNode s : lista) {
            List<QuestaoEnem> questoes = JSON.convertValue(s.get("questions"), new TypeReference<List<QuestaoEnem>>() { });
            Map<String, String> respostas = JSON.convertValue(s.get("answers"), new TypeReference<Map<String, String>>() { });
            feitos.add(new Estatisticas.SimuladoFeito(Instant.parse(s.get("finishedAt").asString()), Placar.de(questoes, respostas)));
        }
        return feitos;
    }

    /** O JSON do app escreve 1 onde o Java escreve 1.0: compara o valor, não a forma. */
    static String numeros(JsonNode no) {
        return no.toString().replaceAll("(\\d)\\.0([,}\\]])", "$1$2");
    }

    static JsonNode arvore(Object valor) {
        return JSON.valueToTree(valor);
    }
}
