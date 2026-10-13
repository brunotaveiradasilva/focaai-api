package com.focaai.api.simulado;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Provas do ENEM inventadas: 180 questões por ano, gabarito sempre "A". Conta quantas foram baixadas. */
@TestConfiguration(proxyBeanMethods = false)
public class EnemDeTeste {

    @Bean
    @Primary
    ProvasFalsas provasFalsas() {
        return new ProvasFalsas();
    }

    public static class ProvasFalsas implements FonteEnem {

        public final AtomicInteger baixadas = new AtomicInteger();
        /** Anos que "não baixam", como uma prova fora do ar na api.enem.dev. */
        public final Set<Integer> falham = new HashSet<>();

        @Override
        public List<QuestaoEnem> baixar(int ano, String lingua) {
            if (falham.contains(ano)) throw new IllegalStateException("prova " + ano + " fora do ar");
            baixadas.incrementAndGet();
            List<QuestaoEnem> questoes = new ArrayList<>();
            for (int n = 1; n <= 180; n++) {
                questoes.add(new QuestaoEnem(ano + "-" + n, ano, n, ApiEnemDev.areaDoNumero(ano, n),
                        "Enunciado " + n + " (" + lingua + ")", "Assinale:", List.of(),
                        List.of(new QuestaoEnem.Alternativa("A", "certa", null), new QuestaoEnem.Alternativa("B", "errada", null)),
                        "A"));
            }
            return questoes;
        }
    }
}
