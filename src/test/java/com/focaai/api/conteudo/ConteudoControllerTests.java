package com.focaai.api.conteudo;

import static org.assertj.core.api.Assertions.assertThat;

import com.focaai.api.BancoDeTeste;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BancoDeTeste.class)
class ConteudoControllerTests {

    @Autowired MockMvcTester mvc;

    @Test
    void resumoTrazOQueOOnboardingPrecisa() {
        var resposta = assertThat(mvc.get().uri("/api/conteudo")).hasStatusOk().bodyJson();
        resposta.extractingPath("$.subjects[0].short").isEqualTo("Mat");
        resposta.extractingPath("$.tiers.alta.simuladosPerMonth").isEqualTo(4);
        resposta.extractingPath("$.trackLabels.ambos").isEqualTo("ENEM + vestibulares");
        resposta.extractingPath("$.topics.length()").isEqualTo(233);
    }

    @Test
    void roteiroExplicaOPesoDeCadaProva() {
        var resposta = assertThat(mvc.get().uri("/api/conteudo/roteiros/mat?provas=enem,fuvest"))
                .hasStatusOk().bodyJson();
        resposta.extractingPath("$[0].exams[0].examId").isEqualTo("enem");
        resposta.extractingPath("$[0].exams[0].mapped").isEqualTo(true);
        resposta.extractingPath("$[0].exams[1].mapped").isEqualTo(false);
        resposta.extractingPath("$[0].exams[1].weight").isEqualTo(0.5);
    }

    @Test
    void retaFinalNaoTemPesoPorProva() {
        var resposta = assertThat(mvc.get().uri("/api/conteudo/roteiros/mat?provas=enem")).hasStatusOk().bodyJson();
        resposta.extractingPath("$[-1].final").isEqualTo(true);
        resposta.extractingPath("$[-1].id").isEqualTo("mat.sim");
    }

    @Test
    void materiaInexistenteDa404() {
        assertThat(mvc.get().uri("/api/conteudo/roteiros/xyz?provas=enem")).hasStatus(404);
    }

    @Test
    void editalEProvaDaUfms() {
        assertThat(mvc.get().uri("/api/conteudo/editais/ufms-2027")).hasStatusOk().bodyJson()
                .extractingPath("$.exam").isEqualTo("Vestibular UFMS 2027 (PSV-UFMS 2027)");
        assertThat(mvc.get().uri("/api/conteudo/provas/ufms-2027")).hasStatusOk().bodyJson()
                .extractingPath("$.estrutura.objetiva.questoes").isEqualTo(60);
    }
}
