package com.focaai.api.simulado;

import java.util.List;

/** De onde vêm as provas do ENEM. Em produção, a api.enem.dev; nos testes, provas inventadas. */
public interface FonteEnem {

    /** Questões de uma prova já convertidas e filtradas (só as que dá para responder). */
    List<QuestaoEnem> baixar(int ano, String lingua);
}
