package com.focaai.api.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.focaai.api.BancoDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.databind.ObjectMapper;

/** Quantas provas o banco baixa: o mínimo para ter questão inédita e variar entre provas. */
@SpringBootTest
@Import(BancoDeTeste.class)
class BancoDeQuestoesTests {

    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;

    EnemDeTeste.ProvasFalsas fonte;
    BancoDeQuestoes banco;

    @BeforeEach
    void bancoVazio() {
        jdbc.sql("DELETE FROM prova_enem").update();
        fonte = new EnemDeTeste.ProvasFalsas();
        banco = new BancoDeQuestoes(fonte, jdbc, json, false);
    }

    @Test
    void comCacheVazioBaixaUmaProvaSo() {
        assertThat(banco.montarSimulado(List.of("matematica"), 10, true, "ingles", Set.of())).hasSize(10);
        assertThat(fonte.baixadas).hasValue(1);
    }

    @Test
    void comUmaProvaEmCacheBaixaMaisUmaParaVariar() {
        banco.montarSimulado(List.of("matematica"), 10, true, "ingles", Set.of());
        banco.montarSimulado(List.of("matematica"), 10, true, "ingles", Set.of());
        assertThat(fonte.baixadas).hasValue(2);
        // Duas em cache e questão inédita sobrando: não baixa mais.
        banco.montarSimulado(List.of("matematica"), 10, true, "ingles", Set.of());
        assertThat(fonte.baixadas).hasValue(2);
    }

    @Test
    void provaBaixadaFicaNoBancoParaAProximaInstancia() {
        banco.montarSimulado(List.of("humanas"), 5, true, "espanhol", Set.of());
        BancoDeQuestoes outra = new BancoDeQuestoes(fonte, jdbc, json, false);
        int ano = jdbc.sql("SELECT ano FROM prova_enem WHERE lingua = 'espanhol'").query(Integer.class).single();
        assertThat(outra.prova(ano, "espanhol")).hasSize(180);
        assertThat(fonte.baixadas).hasValue(1);
    }

    @Test
    void baixaMaisQuandoFaltaQuestaoInedita() {
        // 45 de matemática por prova: pedir 60 inéditas obriga a baixar duas.
        assertThat(banco.montarSimulado(List.of("matematica"), 60, true, "ingles", Set.of())).hasSize(60);
        assertThat(fonte.baixadas).hasValue(2);
    }
}
