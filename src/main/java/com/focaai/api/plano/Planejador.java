package com.focaai.api.plano;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import com.focaai.api.aluno.Perfil;
import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.conteudo.Concorrencia;
import com.focaai.api.conteudo.Curso;
import com.focaai.api.conteudo.Materia;
import com.focaai.api.conteudo.Prova;
import com.focaai.api.conteudo.TopicoNoRoteiro;

import org.springframework.stereotype.Component;

/**
 * Plano de estudos por regras. Determinístico e explicável: cada bloco vem das respostas do
 * onboarding (rotina, concorrência do curso, nível em cada matéria) mais o que o aluno registra, então
 * as mesmas entradas sempre geram a mesma semana. Porte do {@code planner.ts} do app.
 */
@Component
public class Planejador {

    /** Menor trecho que vale um bloco de estudo. */
    static final int BLOCO_MINIMO = 30;

    static final Map<String, String> ROTULO_DA_ATIVIDADE = Map.of(
            "teoria", "Teoria + questões",
            "exercicios", "Bateria de questões",
            "redacao", "Redação",
            "revisao", "Revisão da semana",
            "simulado", "Simulado");

    private final Catalogo catalogo;

    public Planejador(Catalogo catalogo) {
        this.catalogo = catalogo;
    }

    // -------------------------------------------------------------------------------------------
    // Provas
    // -------------------------------------------------------------------------------------------

    public List<Prova> provasSeguidas(Perfil perfil) {
        return catalogo.provas().stream()
                .filter(p -> p.cycle() == perfil.cycle()
                        && ((p.kind().equals("enem") && !perfil.track().equals("vestibulares"))
                        || (p.kind().equals("vestibular") && !perfil.track().equals("enem")
                        && perfil.vestibulares().contains(p.id()))))
                .toList();
    }

    /** Cada dia de prova que o aluno segue a partir de {@code de}, do mais próximo ao mais distante. */
    public List<DiaDeProva> proximasProvas(Perfil perfil, LocalDate de) {
        List<DiaDeProva> dias = new ArrayList<>();
        for (Prova p : provasSeguidas(perfil)) {
            for (Prova.Fase f : p.phases()) {
                for (String d : f.dates()) {
                    LocalDate data = LocalDate.parse(d);
                    if (!data.isBefore(de)) dias.add(new DiaDeProva(p.id(), p.name(), f.label(), data));
                }
            }
        }
        dias.sort(Comparator.comparing(DiaDeProva::date));
        return dias;
    }

    public String objetivo(Perfil perfil) {
        return catalogo.trilhas().get(perfil.track()) + " " + perfil.cycle() + " · " + catalogo.curso(perfil.courseId()).name();
    }

    // -------------------------------------------------------------------------------------------
    // Matérias e assuntos
    // -------------------------------------------------------------------------------------------

    /** Peso de cada matéria na rotação da semana. Redação tem blocos próprios e fica de fora. */
    public Map<String, Double> pesosDasMaterias(Perfil perfil, Map<String, Double> reforco) {
        Curso curso = catalogo.curso(perfil.courseId());
        Map<String, Double> pesos = new LinkedHashMap<>();
        for (Materia m : catalogo.materias()) {
            if (m.id().equals("red")) continue;
            int nivel = perfil.levels().getOrDefault(m.id(), 0);
            pesos.put(m.id(), 1 + (nivel == 1 ? 1.5 : nivel == 3 ? -0.4 : 0)
                    + (curso.focus().contains(m.id()) ? 1 : 0) + reforco.getOrDefault(m.id(), 0.0));
        }
        return pesos;
    }

    /**
     * O foco escolhido pelo aluno vence; senão, continua o que ele começou; senão, o primeiro assunto
     * não terminado que os editais citam explicitamente, depois os citados em parte.
     */
    public TopicoNoRoteiro assuntoAtual(String materia, Map<String, Integer> progresso, Map<String, String> foco,
            List<String> provas) {
        List<TopicoNoRoteiro> roteiro = catalogo.roteiro(materia, provas);
        Optional<TopicoNoRoteiro> focado = roteiro.stream().filter(t -> t.id().equals(foco.get(materia))).findFirst();
        if (focado.isPresent() && !feito(focado.get(), progresso)) return focado.get();
        List<TopicoNoRoteiro> conteudo = roteiro.stream().filter(t -> !t.retaFinal()).toList();
        return conteudo.stream().filter(t -> progresso.getOrDefault(t.id(), 0) > 0 && !feito(t, progresso)).findFirst()
                .or(() -> conteudo.stream().filter(t -> !t.secundario() && !feito(t, progresso)).findFirst())
                .or(() -> conteudo.stream().filter(t -> !feito(t, progresso)).findFirst())
                .orElse(roteiro.get(roteiro.size() - 1));
    }

    /** {@code done}, {@code doing} ou {@code todo}, como o app mostra no roadmap. */
    public static String estadoDoAssunto(int aulas, int feitas) {
        if (feitas >= aulas) return "done";
        return feitas > 0 ? "doing" : "todo";
    }

    private static boolean feito(TopicoNoRoteiro t, Map<String, Integer> progresso) {
        return progresso.getOrDefault(t.id(), 0) >= t.lessons();
    }

    /** Round-robin ponderado suave: determinístico e espalha as matérias pesadas pela semana. */
    static List<String> rotacao(Map<String, Double> pesos, int n, long semente) {
        List<String> ids = new ArrayList<>(pesos.keySet());
        Map<String, Double> peso = new HashMap<>();
        double total = 0;
        for (String k : ids) {
            peso.put(k, Math.max(0.2, pesos.get(k)));
            total += peso.get(k);
        }
        Map<String, Double> atual = new HashMap<>();
        // A semente (número da semana) varia qual matéria abre cada semana.
        for (int i = 0; i < ids.size(); i++) atual.put(ids.get(i), ((i + semente) % ids.size()) * 0.01);
        List<String> saida = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String melhor = ids.get(0);
            for (String k : ids) {
                atual.put(k, atual.get(k) + peso.get(k));
                if (atual.get(k) > atual.get(melhor)) melhor = k;
            }
            atual.put(melhor, atual.get(melhor) - total);
            saida.add(melhor);
        }
        return saida;
    }

    // -------------------------------------------------------------------------------------------
    // Passos: o que fazer dentro de cada bloco
    // -------------------------------------------------------------------------------------------

    private static int r5(double n) {
        return (int) Math.max(5, Math.round(n / 5) * 5);
    }

    /** O ENEM dá mais ou menos 3 minutos por questão. */
    private static long questoesPara(double minutos) {
        return Math.max(5, Math.round(minutos / 3));
    }

    static List<Passo> passos(String atividade, int minutos, String assunto, double fatiaDeQuestoes) {
        return switch (atividade) {
            case "teoria" -> {
                int fixar = r5(minutos * 0.1);
                int q = r5(minutos * fatiaDeQuestoes * 0.6);
                yield List.of(
                        new Passo("Aula ou leitura: " + assunto, minutos - q - fixar),
                        new Passo(questoesPara(q) + " questões de fixação", q),
                        new Passo("Anotar dúvidas e erros", fixar));
            }
            case "exercicios" -> {
                int aquecer = r5(minutos * 0.15);
                int corrigir = r5(minutos * 0.15);
                int q = minutos - aquecer - corrigir;
                yield List.of(
                        new Passo("Revisar o resumo de " + assunto, aquecer),
                        new Passo(questoesPara(q) + " questões de " + assunto, q),
                        new Passo("Corrigir e entender cada erro", corrigir));
            }
            case "redacao" -> minutos >= 75
                    ? List.of(
                            new Passo("Ler a proposta e planejar tese e argumentos", 15),
                            new Passo("Escrever a redação completa", minutos - 30),
                            new Passo("Revisar pelas 5 competências do ENEM", 15))
                    : List.of(
                            new Passo("Ler a proposta e planejar tese e argumentos", r5(minutos * 0.3)),
                            new Passo("Escrever introdução e um desenvolvimento", minutos - r5(minutos * 0.3)));
            case "revisao" -> List.of(
                    new Passo("Reler anotações e erros da semana", r5(minutos * 0.4)),
                    new Passo("Refazer as questões que você errou", r5(minutos * 0.4)),
                    new Passo("Conferir o plano da próxima semana", minutos - 2 * r5(minutos * 0.4)));
            case "simulado" -> {
                int conferir = r5(minutos * 0.15);
                yield List.of(
                        new Passo("Simulado de " + questoesPara(minutos - conferir) + " questões no Banco de Questões", minutos - conferir),
                        new Passo("Conferir o resultado e anotar os erros", conferir));
            }
            default -> throw new IllegalArgumentException(atividade);
        };
    }

    // -------------------------------------------------------------------------------------------
    // A semana
    // -------------------------------------------------------------------------------------------

    record Bloco(int dia, int periodo, int k, int minutos) {
    }

    List<Bloco> blocos(Perfil perfil) {
        List<Bloco> blocos = new ArrayList<>();
        for (int dia = 0; dia < 7; dia++) {
            List<Integer> periodos = new ArrayList<>();
            Map<Integer, Double> livre = new HashMap<>();
            for (int p = 0; p < 3; p++) {
                // Um período marcado só conta se a rotina fixa deixar espaço nele.
                double l = perfil.routine() == null ? Double.POSITIVE_INFINITY : perfil.routine().minutosLivres(dia, p);
                livre.put(p, l);
                if (perfil.estudaEm(p, dia) && l >= BLOCO_MINIMO) periodos.add(p);
            }
            for (int periodo : periodos) {
                double porPeriodo = Math.min((double) perfil.minutesPerDay() / periodos.size(), livre.get(periodo));
                int n = (int) Math.max(1, Math.round(porPeriodo / perfil.sessionMin()));
                int minutos = Math.max(30, r5(porPeriodo / n));
                for (int k = 0; k < n; k++) blocos.add(new Bloco(dia, periodo, k, minutos));
            }
        }
        return blocos;
    }

    /** Espalha {@code quantos} escolhidos por igual entre {@code n} posições. */
    private static List<Integer> espalhar(int n, int quantos) {
        int m = Math.min(n, quantos);
        List<Integer> posicoes = new ArrayList<>();
        for (int i = 0; i < m; i++) posicoes.add((int) Math.floor(((i + 0.5) * n) / m));
        return posicoes;
    }

    public List<Tarefa> semana(Perfil perfil, Map<String, Integer> progresso, Map<String, String> foco,
            Map<String, Double> reforco, LocalDate qualquerDiaDaSemana) {
        LocalDate segunda = Datas.inicioDaSemana(qualquerDiaDaSemana);
        long numeroDaSemana = Math.floorDiv(segunda.toEpochDay(), 7);
        Concorrencia concorrencia = catalogo.concorrenciaDoCurso(perfil.courseId());
        List<String> provas = perfil.provas();
        List<Bloco> blocos = blocos(perfil);
        if (blocos.isEmpty()) return List.of();

        String[] tipo = new String[blocos.size()];
        int ultimo = blocos.size() - 1;

        // Simulado no sábado (ou no último dia de estudo), toda semana ou semana sim, semana não.
        if (concorrencia.simuladosPerMonth() >= 4 || numeroDaSemana % 2 == 0) {
            int i = primeiroNoDia(blocos, 5) >= 0 ? primeiroNoDia(blocos, 5) : primeiroNoDia(blocos, blocos.get(ultimo).dia());
            if (blocos.size() >= 3) tipo[i] = "simulado";
        }
        // Revisão da semana no domingo, quando o aluno estuda nesse dia.
        int domingo = primeiroNoDia(blocos, 6);
        if (domingo >= 0 && tipo[domingo] == null && blocos.size() >= 4) tipo[domingo] = "revisao";
        // Redações espalhadas pela semana.
        List<Integer> livres = livres(tipo);
        for (int pos : espalhar(livres.size(), concorrencia.redacoesPerWeek())) {
            if (livres.size() > 3) tipo[livres.get(pos)] = "redacao";
        }

        List<Integer> estudo = livres(tipo);
        List<String> materias = rotacao(pesosDasMaterias(perfil, reforco), estudo.size(), numeroDaSemana);
        Map<Integer, String> materiaDoBloco = new HashMap<>();
        for (int n = 0; n < estudo.size(); n++) materiaDoBloco.put(estudo.get(n), materias.get(n));
        Map<String, Integer> vezes = new HashMap<>();

        List<Tarefa> tarefas = new ArrayList<>();
        for (int i = 0; i < blocos.size(); i++) {
            Bloco b = blocos.get(i);
            String data = segunda.plusDays(b.dia()).toString();
            String id = data + "-" + b.periodo() + "-" + b.k();
            String atividade = tipo[i];
            if ("simulado".equals(atividade) || "revisao".equals(atividade)) {
                tarefas.add(new Tarefa(id, data, b.dia(), b.periodo(), atividade, null, null, ROTULO_DA_ATIVIDADE.get(atividade),
                        b.minutos(), passos(atividade, b.minutos(), null, concorrencia.questionShare())));
                continue;
            }
            if ("redacao".equals(atividade)) {
                tarefas.add(new Tarefa(id, data, b.dia(), b.periodo(), atividade, "red", null, "Redação com tema de prova anterior",
                        b.minutos(), passos(atividade, b.minutos(), null, concorrencia.questionShare())));
                continue;
            }
            String materia = materiaDoBloco.get(i);
            TopicoNoRoteiro assunto = assuntoAtual(materia, progresso, foco, provas);
            int vez = vezes.merge(materia, 1, Integer::sum);
            // Assunto novo começa pela teoria; depois, um bloco sim, outro não é só prática.
            String ativ = progresso.getOrDefault(assunto.id(), 0) == 0 && vez == 1 ? "teoria"
                    : vez % 2 == 0 ? "exercicios" : "teoria";
            tarefas.add(new Tarefa(id, data, b.dia(), b.periodo(), ativ, materia, assunto.id(), assunto.title(), b.minutos(),
                    passos(ativ, b.minutos(), assunto.title(), concorrencia.questionShare())));
        }
        return tarefas;
    }

    public List<Tarefa> dia(LocalDate data, Perfil perfil, Map<String, Integer> progresso, Map<String, String> foco,
            Map<String, Double> reforco) {
        String chave = data.toString();
        return semana(perfil, progresso, foco, reforco, data).stream().filter(t -> t.date().equals(chave)).toList();
    }

    /** Somado por bloco: a rotina fixa pode deixar menos que a meta diária em alguns dias. */
    public int minutosPorSemana(Perfil perfil) {
        return blocos(perfil).stream().mapToInt(Bloco::minutos).sum();
    }

    /** Dias da semana (0 = segunda) em que o plano tem estudo. */
    public TreeSet<Integer> diasDeEstudo(Perfil perfil) {
        TreeSet<Integer> dias = new TreeSet<>();
        blocos(perfil).forEach(b -> dias.add(b.dia()));
        return dias;
    }

    private static int primeiroNoDia(List<Bloco> blocos, int dia) {
        for (int i = 0; i < blocos.size(); i++) if (blocos.get(i).dia() == dia) return i;
        return -1;
    }

    private static List<Integer> livres(String[] tipo) {
        List<Integer> livres = new ArrayList<>();
        for (int i = 0; i < tipo.length; i++) if (tipo[i] == null) livres.add(i);
        return livres;
    }
}
