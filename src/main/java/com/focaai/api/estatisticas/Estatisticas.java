package com.focaai.api.estatisticas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.aluno.CheckinDoAluno;
import com.focaai.api.aluno.Treinador;
import com.focaai.api.conteudo.AreaEnem;
import com.focaai.api.plano.Datas;
import com.focaai.api.simulado.Placar;

/**
 * Números das telas Início, Evolução e Banco de Questões. Porte do {@code stats.ts} do app. Datas
 * no calendário do aluno ({@code fuso}).
 */
public final class Estatisticas {

    private Estatisticas() {
    }

    /** Um simulado finalizado, só com o que as estatísticas usam. */
    public record SimuladoFeito(Instant finishedAt, Placar score) {
    }

    public record MinutosNoDia(LocalDate date, int minutes) {
    }

    /** {@code pct} nulo = nenhuma questão ainda. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TotalDeQuestoes(int total, int correct, Double pct) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AcertoNaArea(String area, int total, int correct, Double pct) {
    }

    static LocalDate dia(Instant quando, ZoneId fuso) {
        return LocalDate.ofInstant(quando, fuso);
    }

    public static int minutosNoDia(List<CheckinDoAluno> checkins, LocalDate dia, ZoneId fuso) {
        return checkins.stream().filter(c -> dia(c.at(), fuso).equals(dia)).mapToInt(CheckinDoAluno::minutes).sum();
    }

    /** Um dia conta quando o aluno registrou estudo de verdade ou finalizou um simulado. */
    static Set<LocalDate> diasAtivos(List<CheckinDoAluno> checkins, List<SimuladoFeito> simulados, ZoneId fuso) {
        Set<LocalDate> dias = new HashSet<>();
        for (CheckinDoAluno c : checkins) if (c.minutes() >= Treinador.MINUTOS_MINIMOS) dias.add(dia(c.at(), fuso));
        for (SimuladoFeito s : simulados) dias.add(dia(s.finishedAt(), fuso));
        return dias;
    }

    /**
     * Dias seguidos de estudo. Dias de folga na rotina do próprio aluno não quebram a sequência, e
     * hoje só conta depois que acontece, para a sequência não cair no café da manhã.
     */
    public static int sequencia(Set<Integer> diasDeEstudo, List<CheckinDoAluno> checkins, List<SimuladoFeito> simulados,
            LocalDate hoje, ZoneId fuso) {
        Set<LocalDate> ativos = diasAtivos(checkins, simulados, fuso);
        int conta = 0;
        LocalDate d = ativos.contains(hoje) ? hoje : hoje.minusDays(1);
        for (int i = 0; i < 400; i++) {
            if (ativos.contains(d)) conta++;
            else if (diasDeEstudo.contains(Datas.diaDaSemana(d))) break;
            d = d.minusDays(1);
        }
        return conta;
    }

    public static int melhorSequencia(Set<Integer> diasDeEstudo, List<CheckinDoAluno> checkins, List<SimuladoFeito> simulados,
            ZoneId fuso) {
        TreeSet<LocalDate> ativos = new TreeSet<>(diasAtivos(checkins, simulados, fuso));
        if (ativos.isEmpty()) return 0;
        int melhor = 0;
        int atual = 0;
        for (LocalDate d = ativos.first(); !d.isAfter(ativos.last()); d = d.plusDays(1)) {
            if (ativos.contains(d)) melhor = Math.max(melhor, ++atual);
            else if (diasDeEstudo.contains(Datas.diaDaSemana(d))) atual = 0;
        }
        return melhor;
    }

    /** Minutos estudados em cada um dos últimos {@code n} dias, do mais antigo até hoje. */
    public static List<MinutosNoDia> ultimosDias(List<CheckinDoAluno> checkins, int n, LocalDate hoje, ZoneId fuso) {
        List<MinutosNoDia> dias = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate d = hoje.minusDays(n - 1 - i);
            dias.add(new MinutosNoDia(d, minutosNoDia(checkins, d, fuso)));
        }
        return dias;
    }

    public static Map<String, Integer> minutosPorMateria(List<CheckinDoAluno> checkins, LocalDate desde, ZoneId fuso) {
        Map<String, Integer> minutos = new LinkedHashMap<>();
        for (CheckinDoAluno c : checkins) {
            if (c.subject() == null || dia(c.at(), fuso).isBefore(desde)) continue;
            minutos.merge(c.subject(), c.minutes(), Integer::sum);
        }
        return minutos;
    }

    public static TotalDeQuestoes questoes(List<CheckinDoAluno> checkins, List<SimuladoFeito> simulados) {
        int total = 0;
        int certas = 0;
        for (CheckinDoAluno c : checkins) {
            total += c.questions();
            certas += c.correct();
        }
        for (SimuladoFeito s : simulados) {
            total += s.score().total();
            certas += s.score().correct();
        }
        return new TotalDeQuestoes(total, certas, total > 0 ? (double) certas / total : null);
    }

    public static List<AcertoNaArea> acertoPorArea(List<AreaEnem> areas, List<SimuladoFeito> simulados) {
        List<AcertoNaArea> lista = new ArrayList<>();
        for (AreaEnem area : areas) {
            int total = 0;
            int certas = 0;
            for (SimuladoFeito s : simulados) {
                Placar.PorArea a = s.score().byArea().get(area.id());
                if (a != null) {
                    total += a.total();
                    certas += a.correct();
                }
            }
            lista.add(new AcertoNaArea(area.id(), total, certas, total > 0 ? (double) certas / total : null));
        }
        return lista;
    }
}
