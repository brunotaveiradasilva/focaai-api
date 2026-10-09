package com.focaai.api.simulado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Acertos de um simulado, no total e por área. Porte do {@code scoreOf} do app. */
public record Placar(int total, int correct, Map<String, PorArea> byArea) {

    public record PorArea(int total, int correct) {
    }

    public static Placar de(List<QuestaoEnem> questoes, Map<String, String> respostas) {
        Map<String, int[]> contas = new LinkedHashMap<>();
        int acertos = 0;
        for (QuestaoEnem q : questoes) {
            boolean certa = q.correct() != null && q.correct().equals(respostas.get(q.key()));
            if (certa) acertos++;
            int[] area = contas.computeIfAbsent(q.area(), k -> new int[2]);
            area[0]++;
            if (certa) area[1]++;
        }
        Map<String, PorArea> porArea = new LinkedHashMap<>();
        contas.forEach((area, c) -> porArea.put(area, new PorArea(c[0], c[1])));
        return new Placar(questoes.size(), acertos, porArea);
    }
}
