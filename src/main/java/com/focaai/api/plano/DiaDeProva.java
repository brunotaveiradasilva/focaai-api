package com.focaai.api.plano;

import java.time.LocalDate;

/** Um dia de prova que o aluno segue (ex.: ENEM 2026, 1º dia, 2026-11-08). */
public record DiaDeProva(String examId, String examName, String phase, LocalDate date) {
}
