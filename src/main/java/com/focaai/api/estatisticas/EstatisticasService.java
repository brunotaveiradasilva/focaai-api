package com.focaai.api.estatisticas;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.aluno.AlunoService;
import com.focaai.api.aluno.CheckinDoAluno;
import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.conteudo.TopicoNoRoteiro;
import com.focaai.api.plano.Planejador;
import com.focaai.api.simulado.SimuladoService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstatisticasService {

    private final AlunoService alunos;
    private final SimuladoService simulados;
    private final Planejador planejador;
    private final Catalogo catalogo;

    public EstatisticasService(AlunoService alunos, SimuladoService simulados, Planejador planejador, Catalogo catalogo) {
        this.alunos = alunos;
        this.simulados = simulados;
        this.planejador = planejador;
        this.catalogo = catalogo;
    }

    /** Tudo da tela Evolução (e a sequência e os minutos de hoje da tela Início), nos últimos {@code dias}. */
    @Transactional(readOnly = true)
    public Resumo resumo(UUID usuarioId, LocalDate hoje, ZoneId fuso, int dias) {
        AlunoService.DadosDoAluno aluno = alunos.dados(usuarioId);
        Set<Integer> diasDeEstudo = planejador.diasDeEstudo(aluno.perfil());
        List<CheckinDoAluno> checkins = alunos.checkins(usuarioId);
        List<Estatisticas.SimuladoFeito> feitos = simulados.finalizados(usuarioId).stream()
                .map(s -> new Estatisticas.SimuladoFeito(s.finishedAt(), s.score()))
                .toList();
        int sequencia = Estatisticas.sequencia(diasDeEstudo, checkins, feitos, hoje, fuso);
        return new Resumo(
                hoje,
                sequencia,
                Math.max(sequencia, Estatisticas.melhorSequencia(diasDeEstudo, checkins, feitos, fuso)),
                Estatisticas.minutosNoDia(checkins, hoje, fuso),
                Estatisticas.ultimosDias(checkins, dias, hoje, fuso),
                Estatisticas.minutosPorMateria(checkins, hoje.minusDays(dias - 1), fuso),
                Estatisticas.questoes(checkins, feitos),
                Estatisticas.acertoPorArea(catalogo.areas(), feitos),
                checkins.size(),
                feitos.size(),
                roadmap(aluno));
    }

    /** Aulas feitas sobre o total do roadmap de cada matéria, nas provas que o aluno segue. */
    private List<ProgressoNaMateria> roadmap(AlunoService.DadosDoAluno aluno) {
        return catalogo.materias().stream().map(m -> {
            int feitas = 0;
            int total = 0;
            for (TopicoNoRoteiro t : catalogo.roteiro(m.id(), aluno.perfil().provas())) {
                feitas += Math.min(t.lessons(), aluno.progresso().getOrDefault(t.id(), 0));
                total += t.lessons();
            }
            return new ProgressoNaMateria(m.id(), feitas, total);
        }).toList();
    }

    /**
     * @param bestStreak     nunca menor que a sequência atual
     * @param minutesBySubject minutos por matéria no período
     * @param checkins       quantos check-ins o aluno tem no total
     * @param simulados      quantos simulados finalizados
     * @param roadmap        aulas feitas e total do roadmap de cada matéria
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Resumo(
            LocalDate today,
            int streak,
            int bestStreak,
            int minutesToday,
            List<Estatisticas.MinutosNoDia> days,
            Map<String, Integer> minutesBySubject,
            Estatisticas.TotalDeQuestoes questions,
            List<Estatisticas.AcertoNaArea> areas,
            int checkins,
            int simulados,
            List<ProgressoNaMateria> roadmap) {
    }

    public record ProgressoNaMateria(String subject, int done, int total) {
    }
}
