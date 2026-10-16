package com.focaai.api.aluno;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.focaai.api.conteudo.Catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * O treinador da API decide igual ao {@code coach.ts} do app. {@code referencia/treinador.json} tem
 * 8.100 check-ins avaliados pelo app (atividade × minutos × sensação × questões × acertos).
 */
class TreinadorTests {

    static final ObjectMapper JSON = JsonMapper.builder().build();

    @Test
    void decideIgualAoApp() throws IOException {
        Treinador treinador = new Treinador(new Catalogo(JSON, List.of("ufms-2027")));
        JsonNode casos;
        try (InputStream in = new ClassPathResource("referencia/treinador.json").getInputStream()) {
            casos = JSON.readTree(in);
        }
        for (JsonNode caso : casos) {
            JsonNode e = caso.get("input");
            Treinador.Avaliacao a = treinador.avaliar(e.get("activity").asString(), texto(e, "subject"), texto(e, "topicId"),
                    e.get("minutes").asInt(), e.get("feeling").asInt(), e.get("questions").asInt(), e.get("correct").asInt());

            JsonNode esperado = caso.get("output");
            String descricao = e.toString();
            assertThat(a.aulas()).as(descricao).isEqualTo(esperado.get("lessons").asInt());
            JsonNode ajuste = esperado.get("adj");
            if (ajuste == null) {
                assertThat(a.ajuste()).as(descricao).isNull();
            } else {
                assertThat(a.ajuste()).as(descricao).isNotNull();
                assertThat(a.ajuste().texto()).as(descricao).isEqualTo(ajuste.get("text").asString());
                assertThat(a.ajuste().reforco()).as(descricao).isEqualTo(ajuste.get("boost").asDouble());
            }
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode v = no.get(campo);
        return v == null || v.isNull() ? null : v.asString();
    }
}
