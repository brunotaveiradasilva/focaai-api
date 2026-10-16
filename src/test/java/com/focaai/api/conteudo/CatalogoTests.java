package com.focaai.api.conteudo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * O roadmap da API tem que ser exatamente o que o app montava sozinho. {@code referencia/roteiros.json}
 * foi gerado rodando o {@code catalog.ts} do app em várias combinações de matéria e prova, quando só
 * os editais do ENEM e da UFMS estavam mapeados; por isso o catálogo aqui não carrega o da Fuvest.
 */
class CatalogoTests {

    static final ObjectMapper JSON = JsonMapper.builder().build();
    static Catalogo catalogo;
    static JsonNode referencia;

    @BeforeAll
    static void carregar() throws IOException {
        catalogo = new Catalogo(JSON, List.of("ufms-2027"));
        try (InputStream in = new ClassPathResource("referencia/roteiros.json").getInputStream()) {
            referencia = JSON.readTree(in);
        }
    }

    @Test
    void temOsMesmosAssuntosDoApp() {
        assertThat(catalogo.topicos()).hasSize(referencia.get("totalTopicos").asInt());
    }

    @Test
    void montaOsMesmosRoteirosDoApp() {
        for (JsonNode r : referencia.get("roteiros")) {
            List<String> provas = new ArrayList<>();
            r.get("provas").forEach(p -> provas.add(p.asString()));
            String materia = r.get("materia").asString();
            List<TopicoNoRoteiro> roteiro = catalogo.roteiro(materia, provas);

            String caso = materia + " " + provas;
            assertThat(roteiro).as(caso).hasSize(r.get("topicos").size());
            for (int i = 0; i < roteiro.size(); i++) {
                JsonNode esperado = r.get("topicos").get(i);
                TopicoNoRoteiro t = roteiro.get(i);
                assertThat(t.id()).as(caso).isEqualTo(esperado.get("id").asString());
                assertThat(t.title()).as(t.id()).isEqualTo(esperado.get("title").asString());
                assertThat(t.groupId()).as(t.id()).isEqualTo(esperado.get("groupId").asString());
                assertThat(t.groupName()).as(t.id()).isEqualTo(esperado.get("groupName").asString());
                assertThat(t.lessons()).as(t.id()).isEqualTo(esperado.get("lessons").asInt());
                assertThat(t.weight()).as(t.id()).isEqualTo(esperado.get("weight").asDouble());
                assertThat(t.retaFinal()).as(t.id()).isEqualTo(esperado.get("final").asBoolean());
            }
        }
    }

    @Test
    void pesoPorProvaIgualAoDoApp() {
        for (JsonNode p : referencia.get("pesos")) {
            String prova = p.get("prova").asString();
            for (JsonNode par : p.get("amostra")) {
                assertThat(catalogo.pesoNaProva(prova, par.get(0).asString()))
                        .as(prova + " " + par.get(0).asString())
                        .isEqualTo(par.get(1).asDouble());
            }
        }
    }

    @Test
    void cursoDesconhecidoViraOutro() {
        assertThat(catalogo.curso("nao-existe").id()).isEqualTo("outro");
    }
}
