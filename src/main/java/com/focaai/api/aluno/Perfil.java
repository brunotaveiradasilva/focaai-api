package com.focaai.api.aluno;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Respostas do onboarding. Mesmos campos e formatos do {@code Profile} do app.
 *
 * @param levels  autoavaliação por matéria: 1 = tenho dificuldade, 2 = razoável, 3 = domino
 * @param week    21 células, índice = período * 7 + dia (período 0 manhã, 1 tarde, 2 noite; dia 0 = segunda)
 * @param routine compromissos fixos; ausente em perfis criados antes desse passo existir
 * @param cycle   ano em que o aluno faz as provas
 */
public record Perfil(
        @NotBlank String name,
        @NotNull @Pattern(regexp = "enem|vestibulares|ambos") String track,
        @NotNull List<String> vestibulares,
        @Min(2020) @Max(2100) int cycle,
        @NotBlank String courseId,
        @NotNull @Pattern(regexp = "em1|em2|em3|formado|cursinho") String stage,
        boolean firstAttempt,
        @NotNull @Pattern(regexp = "ingles|espanhol") String language,
        @NotNull Map<String, @Min(1) @Max(3) Integer> levels,
        @NotNull @Size(min = 21, max = 21) List<Boolean> week,
        @Min(15) @Max(16 * 60) int minutesPerDay,
        @Min(15) @Max(4 * 60) int sessionMin,
        @Valid Rotina routine,
        @NotNull List<String> obstacles,
        @NotBlank String createdAt) {

    /**
     * Provas cujos editais montam o roadmap, independente do ciclo (o calendário pode ainda não ter o
     * ciclo do aluno). Sem nada escolhido, vale o ENEM.
     */
    public List<String> provas() {
        List<String> ids = new ArrayList<>();
        if (!track.equals("vestibulares")) ids.add("enem");
        if (!track.equals("enem")) ids.addAll(vestibulares);
        return ids.isEmpty() ? List.of("enem") : ids;
    }

    public int nivel(String materia) {
        return levels.getOrDefault(materia, 2);
    }

    public boolean estudaEm(int periodo, int dia) {
        return Boolean.TRUE.equals(week.get(periodo * 7 + dia));
    }
}
