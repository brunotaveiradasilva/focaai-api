package com.focaai.api.plano;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.focaai.api.aluno.AlunoService;
import com.focaai.api.aluno.CheckinDoAluno;
import com.focaai.api.aluno.Perfil;
import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.conteudo.Prova;
import com.focaai.api.conteudo.TopicoNoRoteiro;
import com.focaai.api.simulado.SimuladoService;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A semana de estudos do aluno, o roadmap com o progresso dele e a prévia do onboarding. */
@Service
public class PlanoService {

    private final Planejador planejador;
    private final PlanoRepository planos;
    private final AlunoService alunos;
    private final SimuladoService simulados;
    private final Catalogo catalogo;

    public PlanoService(Planejador planejador, PlanoRepository planos, AlunoService alunos, SimuladoService simulados,
            Catalogo catalogo) {
        this.planejador = planejador;
        this.planos = planos;
        this.alunos = alunos;
        this.simulados = simulados;
        this.catalogo = catalogo;
    }

    /**
     * A semana de {@code hoje}. Dias passados e hoje usam o plano congelado; os próximos mostram o
     * plano vivo. O plano de hoje é congelado na primeira vez que o aluno abre o dia.
     */
    @Transactional
    public Semana semana(UUID usuarioId, LocalDate hoje, ZoneId fuso) {
        AlunoService.DadosDoAluno d = alunos.dados(usuarioId);
        List<Tarefa> vivo = planejador.semana(d.perfil(), d.progresso(), d.foco(), d.reforco(), hoje);
        LocalDate segunda = Datas.inicioDaSemana(hoje);
        Map<LocalDate, List<Tarefa>> congelados = planos.congelados(usuarioId, hoje.minusDays(7), hoje);

        List<Tarefa> tarefas = new ArrayList<>();
        for (int dia = 0; dia < 7; dia++) {
            LocalDate data = segunda.plusDays(dia);
            String chave = data.toString();
            if (!data.isAfter(hoje) && congelados.containsKey(data)) tarefas.addAll(congelados.get(data));
            else vivo.stream().filter(t -> t.date().equals(chave)).forEach(tarefas::add);
        }
        if (!congelados.containsKey(hoje)) {
            List<Tarefa> deHoje = tarefas.stream().filter(t -> t.date().equals(hoje.toString())).toList();
            planos.congelar(usuarioId, hoje, deHoje);
            congelados.put(hoje, deHoje);
        }

        List<CheckinDoAluno> checkins = alunos.checkins(usuarioId);
        List<Instant> finalizados = simulados.finalizacoes(usuarioId);
        Set<String> feitas = Conclusoes.feitas(checkins, finalizados, tarefas, fuso);
        return new Semana(hoje, tarefas, new TreeSet<>(feitas), atrasadas(usuarioId, congelados, checkins, finalizados, hoje, fuso),
                resumo(d.perfil(), hoje));
    }

    /** Tarefas planejadas na última semana que não foram feitas nem dispensadas. */
    private List<Tarefa> atrasadas(UUID usuarioId, Map<LocalDate, List<Tarefa>> congelados, List<CheckinDoAluno> checkins,
            List<Instant> finalizados, LocalDate hoje, ZoneId fuso) {
        List<Tarefa> passadas = new ArrayList<>();
        congelados.forEach((dia, tarefas) -> {
            if (dia.isBefore(hoje) && !dia.isBefore(hoje.minusDays(7))) passadas.addAll(tarefas);
        });
        Set<String> feitas = Conclusoes.feitas(checkins, finalizados, passadas, fuso);
        Set<String> dispensadas = planos.dispensadas(usuarioId);
        return passadas.stream().filter(t -> !feitas.contains(t.id()) && !dispensadas.contains(t.id())).toList();
    }

    @Transactional
    public void dispensar(UUID usuarioId, String tarefaId) {
        planos.dispensar(usuarioId, tarefaId);
    }

    /** Roadmap de uma matéria com o progresso do aluno e o assunto em que ele está. */
    @Transactional(readOnly = true)
    public RoteiroDoAluno roteiro(UUID usuarioId, String materia) {
        catalogo.materia(materia);
        AlunoService.DadosDoAluno d = alunos.dados(usuarioId);
        List<String> provas = d.perfil().provas();
        List<RoteiroDoAluno.Assunto> assuntos = catalogo.roteiro(materia, provas).stream()
                .map(t -> {
                    int feitas = Math.min(t.lessons(), d.progresso().getOrDefault(t.id(), 0));
                    return new RoteiroDoAluno.Assunto(t, Planejador.estadoDoAssunto(t.lessons(), feitas), feitas,
                            t.id().equals(d.foco().get(materia)));
                })
                .toList();
        TopicoNoRoteiro atual = planejador.assuntoAtual(materia, d.progresso(), d.foco(), provas);
        return new RoteiroDoAluno(materia, atual.id(), assuntos);
    }

    /** Objetivo, carga semanal e provas do aluno, para Início, Perfil e Pessoas. */
    @Transactional(readOnly = true)
    public Resumo resumo(UUID usuarioId, LocalDate hoje) {
        return resumo(alunos.dados(usuarioId).perfil(), hoje);
    }

    /** O plano que um perfil ainda não salvo teria: o onboarding mostra antes de terminar. */
    public Previa previa(Perfil perfil, LocalDate hoje) {
        return new Previa(planejador.semana(perfil, Map.of(), Map.of(), Map.of(), hoje), resumo(perfil, hoje),
                tempoLivre(perfil));
    }

    /** Minutos livres por dia (0 = segunda) e período, ou {@code null} sem rotina cadastrada. */
    private static int[][] tempoLivre(Perfil perfil) {
        if (perfil.routine() == null) return null;
        int[][] livre = new int[7][3];
        for (int dia = 0; dia < 7; dia++) {
            for (int periodo = 0; periodo < 3; periodo++) livre[dia][periodo] = perfil.routine().minutosLivres(dia, periodo);
        }
        return livre;
    }

    private Resumo resumo(Perfil perfil, LocalDate hoje) {
        return new Resumo(planejador.objetivo(perfil), planejador.minutosPorSemana(perfil), planejador.diasDeEstudo(perfil),
                planejador.provasSeguidas(perfil), planejador.proximasProvas(perfil, hoje));
    }

    @EventListener
    public void aoMudarOPerfil(AlunoService.PerfilAlterado evento) {
        planos.descongelarTudo(evento.usuarioId());
    }

    @EventListener
    public void aoZerar(AlunoService.DadosZerados evento) {
        planos.descongelarTudo(evento.usuarioId());
        planos.apagarDispensadas(evento.usuarioId());
    }

    /**
     * @param done    ids das tarefas da semana já feitas
     * @param overdue tarefas da última semana que ficaram para trás
     */
    public record Semana(LocalDate today, List<Tarefa> tasks, Set<String> done, List<Tarefa> overdue, Resumo summary) {
    }

    public record Resumo(String goalLabel, int weeklyMinutes, Set<Integer> studyDays, List<Prova> exams,
            List<DiaDeProva> examDates) {
    }

    /**
     * @param free minutos livres que a rotina deixa em cada dia e período ({@code free[dia][periodo]}),
     *             para o onboarding e o editor de rotina mostrarem enquanto o aluno edita
     */
    public record Previa(List<Tarefa> tasks, Resumo summary, int[][] free) {
    }
}
