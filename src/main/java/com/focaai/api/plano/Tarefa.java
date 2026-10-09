package com.focaai.api.plano;

import java.util.List;

/**
 * Um bloco do plano, no formato do {@code Task} do app. {@code id} é {@code data-periodo-k}, estável
 * enquanto o plano daquele dia não muda; {@code day} 0 = segunda; {@code period} 0 manhã, 1 tarde, 2 noite.
 */
public record Tarefa(
        String id,
        String date,
        int day,
        int period,
        String activity,
        String subject,
        String topicId,
        String title,
        int minutes,
        List<Passo> steps) {
}
