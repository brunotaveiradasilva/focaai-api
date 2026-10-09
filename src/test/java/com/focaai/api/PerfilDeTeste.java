package com.focaai.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Respostas de onboarding de exemplo: ENEM 2026, Outras engenharias, estuda toda noite 3h. */
public final class PerfilDeTeste {

    private PerfilDeTeste() {
    }

    public static Map<String, Object> perfil() {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Teste");
        p.put("track", "enem");
        p.put("vestibulares", List.of());
        p.put("cycle", 2026);
        p.put("courseId", "engenharias");
        p.put("stage", "em3");
        p.put("firstAttempt", true);
        p.put("language", "ingles");
        p.put("levels", Map.of("mat", 3, "por", 2, "red", 2, "fis", 1, "qui", 2, "bio", 2, "his", 2, "geo", 2));
        List<Boolean> semana = new ArrayList<>(Collections.nCopies(21, false));
        for (int dia = 0; dia < 7; dia++) semana.set(14 + dia, true);
        p.put("week", semana);
        p.put("minutesPerDay", 180);
        p.put("sessionMin", 90);
        p.put("routine", Map.of("wake", 390, "sleep", 1380, "lunchStart", 720, "lunchEnd", 780, "items", List.of()));
        p.put("obstacles", List.of("celular"));
        p.put("createdAt", "2026-10-09T12:00:00.000Z");
        return p;
    }
}
