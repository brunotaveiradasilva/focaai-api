package com.focaai.api.aluno;

import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.conteudo.Topico;

import org.springframework.stereotype.Component;

/** Lê um check-in e decide como a preparação muda. Porte do {@code coach.ts} do app. */
@Component
public class Treinador {

    /** Check-ins mais curtos ficam no histórico, mas não concluem tarefa nem mexem no plano. */
    public static final int MINUTOS_MINIMOS = 5;

    private final Catalogo catalogo;

    public Treinador(Catalogo catalogo) {
        this.catalogo = catalogo;
    }

    /**
     * @param aulas  aulas a creditar no assunto
     * @param ajuste mudança no plano, ou {@code null}; {@code reforco} nulo = sem mudança de peso
     */
    public record Avaliacao(int aulas, AjusteDoPlano ajuste) {
    }

    public record AjusteDoPlano(Double reforco, String texto) {
    }

    public Avaliacao avaliar(String atividade, String materiaId, String topicoId, int minutos, int sensacao,
            int questoes, int acertos) {
        if (minutos < MINUTOS_MINIMOS || materiaId == null) return new Avaliacao(0, null);

        String materia = catalogo.materia(materiaId).name();
        String topico = topicoId == null ? materia
                : catalogo.topico(topicoId).map(Topico::title).orElse(materia);
        Double acerto = questoes > 0 ? (double) acertos / questoes : null;

        // Mais ou menos uma aula a cada 30 minutos de teoria; a prática consolida mais devagar.
        int aulas = switch (atividade) {
            case "teoria" -> Math.max(1, (int) Math.round(minutos / 30.0));
            case "exercicios" -> Math.max(1, (int) Math.round(minutos / 45.0));
            default -> 0;
        };
        if (sensacao == 3) aulas = Math.max(aulas > 0 ? 1 : 0, aulas - 1);

        if (acerto != null && questoes >= 5 && acerto < 0.6) {
            // Nenhuma aula creditada: o assunto continua o atual e volta de verdade no plano.
            return new Avaliacao(0, new AjusteDoPlano(1.0,
                    "Você acertou %d de %d questões de %s. %s ganhou mais espaço na sua semana e você continua nesse assunto até firmar a base."
                            .formatted(acertos, questoes, topico, materia)));
        }
        if (sensacao == 3 && atividade.equals("teoria")) {
            return new Avaliacao(aulas, new AjusteDoPlano(0.5,
                    "%s foi difícil hoje. Colocamos mais blocos de %s nos próximos dias para você praticar com calma."
                            .formatted(topico, materia)));
        }
        if (acerto != null && questoes >= 5 && acerto >= 0.8) {
            return new Avaliacao(aulas + 1, new AjusteDoPlano(-0.5,
                    "%d%% de acerto em %s. Você avançou mais rápido e liberamos tempo para outras matérias."
                            .formatted(Math.round(acerto * 100), topico)));
        }
        return new Avaliacao(aulas, null);
    }
}
