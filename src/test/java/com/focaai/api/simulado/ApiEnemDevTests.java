package com.focaai.api.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Conversão das questões da api.enem.dev: mesmas regras do {@code enem.ts} do app. */
class ApiEnemDevTests {

    static final String PAGINA = """
            [
              {"index": 1, "language": "ingles", "context": "![](https://enem.dev/x.png)", "files": ["https://enem.dev/x.png"],
               "correctAlternative": "B", "alternativesIntroduction": "Esse cartaz sugere que",
               "alternatives": [{"letter": "A", "text": "a", "file": null}, {"letter": "B", "text": "b", "file": null}]},
              {"index": 1, "language": "espanhol", "context": "otro", "files": [], "correctAlternative": "C",
               "alternativesIntroduction": null,
               "alternatives": [{"letter": "A", "text": "a", "file": null}, {"letter": "C", "text": "c", "file": null}]},
              {"index": 2, "language": null, "context": "ver broken-image aqui", "files": [], "correctAlternative": "A",
               "alternatives": [{"letter": "A", "text": "a", "file": null}, {"letter": "B", "text": "b", "file": null}]},
              {"index": 3, "language": null, "context": null, "files": [], "correctAlternative": null,
               "alternatives": [{"letter": "A", "text": "a", "file": null}, {"letter": "B", "text": "b", "file": null}]},
              {"index": 46, "language": null, "context": "texto", "files": [], "correctAlternative": "D",
               "alternatives": [{"letter": "D", "text": null, "file": "https://enem.dev/d.png"}, {"letter": "E", "text": "e", "file": null}]},
              {"index": 46, "language": null, "context": "duplicada", "files": [], "correctAlternative": "D",
               "alternatives": [{"letter": "D", "text": "d", "file": null}, {"letter": "E", "text": "e", "file": null}]}
            ]
            """;

    @Test
    void filtraEConverteComoOApp() {
        List<JsonNode> brutas = new ArrayList<>();
        JsonMapper.builder().build().readTree(PAGINA).forEach(brutas::add);

        List<QuestaoEnem> questoes = ApiEnemDev.converter(2023, "ingles", brutas);

        assertThat(questoes).extracting(QuestaoEnem::key).containsExactly("2023-1", "2023-46");
        QuestaoEnem primeira = questoes.get(0);
        assertThat(primeira.area()).isEqualTo("linguagens");
        assertThat(primeira.intro()).isEqualTo("Esse cartaz sugere que");
        assertThat(primeira.correct()).isEqualTo("B");
        QuestaoEnem segunda = questoes.get(1);
        assertThat(segunda.area()).isEqualTo("humanas");
        assertThat(segunda.intro()).isEmpty();
        assertThat(segunda.context()).isEqualTo("texto");
        assertThat(segunda.alternatives().get(0).file()).isEqualTo("https://enem.dev/d.png");
    }

    @Test
    void areaPeloNumeroSegueAOrdemDeCadaEdicao() {
        assertThat(ApiEnemDev.areaDoNumero(2017, 1)).isEqualTo("linguagens");
        assertThat(ApiEnemDev.areaDoNumero(2017, 91)).isEqualTo("natureza");
        assertThat(ApiEnemDev.areaDoNumero(2016, 1)).isEqualTo("humanas");
        assertThat(ApiEnemDev.areaDoNumero(2016, 91)).isEqualTo("linguagens");
        assertThat(ApiEnemDev.areaDoNumero(2016, 180)).isEqualTo("matematica");
        assertThat(ApiEnemDev.areaDoNumero(2023, 185)).isEqualTo("matematica");
    }
}
