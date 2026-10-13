package com.focaai.api.aluno;

import java.time.Instant;
import java.util.UUID;

/** Um check-in como o app conhece ({@code Checkin} do store). */
public record CheckinDoAluno(
        UUID id,
        Instant at,
        String taskId,
        String subject,
        String topicId,
        int minutes,
        int feeling,
        int questions,
        int correct) {

    static CheckinDoAluno de(Checkin c) {
        return new CheckinDoAluno(c.getId(), c.getFeitoEm(), c.getTarefaId(), c.getMateria(), c.getTopicoId(),
                c.getMinutos(), c.getSensacao(), c.getQuestoes(), c.getAcertos());
    }
}
