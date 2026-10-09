package com.focaai.api.pessoas;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.focaai.api.aluno.Perfil;
import com.focaai.api.conteudo.Catalogo;
import com.focaai.api.conteudo.Curso;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Parceiros de estudo e grupos. Enquanto não há perfis reais para cruzar, roda sobre pessoas de
 * exemplo (todas fictícias, e a tela avisa); a lógica de combinação é a de verdade e vai rodar igual
 * sobre perfis reais. Porte do {@code people.ts} do app.
 */
@Component
public class Pessoas {

    static final String[] PERIODO = {"manhã", "tarde", "noite"};

    private final Catalogo catalogo;
    private final List<Pessoa> exemplos;

    public Pessoas(Catalogo catalogo, ObjectMapper json) {
        this.catalogo = catalogo;
        try (InputStream in = new ClassPathResource("conteudo/pessoas-exemplo.json").getInputStream()) {
            this.exemplos = json.readValue(in, new TypeReference<List<Pessoa>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Pessoa> exemplos() {
        return exemplos;
    }

    static List<Integer> periodos(Perfil p) {
        List<Integer> periodos = new ArrayList<>();
        for (int periodo = 0; periodo < 3; periodo++) {
            for (int dia = 0; dia < 7; dia++) {
                if (p.estudaEm(periodo, dia)) {
                    periodos.add(periodo);
                    break;
                }
            }
        }
        return periodos;
    }

    private static List<String> comNivel(Perfil p, int nivel) {
        return p.levels().entrySet().stream().filter(e -> e.getValue() == nivel).map(Map.Entry::getKey).toList();
    }

    /** Pessoas da mais para a menos compatível, com os motivos em texto. */
    public List<Combinacao> combinar(Perfil perfil) {
        Curso meuCurso = catalogo.curso(perfil.courseId());
        List<Integer> meusPeriodos = periodos(perfil);
        List<String> minhasDificuldades = comNivel(perfil, 1);
        List<String> meusFortes = comNivel(perfil, 3);

        List<Combinacao> lista = new ArrayList<>();
        for (Pessoa pessoa : exemplos) {
            int pontos = 0;
            List<String> motivos = new ArrayList<>();
            Curso curso = catalogo.curso(pessoa.courseId());
            if (pessoa.courseId().equals(perfil.courseId())) {
                pontos += 30;
                motivos.add("Também quer " + curso.name());
            } else if (curso.tier().equals(meuCurso.tier())) {
                pontos += 15;
                motivos.add("Curso de concorrência " + catalogo.concorrencia().get(curso.tier()).label().toLowerCase()
                        + ", como o seu");
            }
            if (pessoa.cycle() == perfil.cycle() && (pessoa.track().equals(perfil.track()) || pessoa.track().equals("ambos")
                    || perfil.track().equals("ambos"))) {
                pontos += 20;
                motivos.add("Mesma prova em " + pessoa.cycle());
            }
            List<Integer> emComum = pessoa.periods().stream().filter(meusPeriodos::contains).toList();
            if (!emComum.isEmpty()) {
                pontos += 20;
                motivos.add("Estuda de " + emComum.stream().map(p -> PERIODO[p]).collect(Collectors.joining(" e ")) + ", como você");
            }
            // Forças que se completam fazem os melhores parceiros de estudo.
            long ajuda = pessoa.strong().stream().filter(minhasDificuldades::contains).count();
            if (ajuda > 0) pontos += 15 + 5 * (int) ajuda;
            if (pessoa.weak().stream().anyMatch(meusFortes::contains)) pontos += 5;
            lista.add(new Combinacao(pessoa, Math.min(99, pontos + 10), motivos, ajudaEm(perfil, pessoa)));
        }
        lista.sort(Comparator.comparingInt(Combinacao::score).reversed());
        return lista;
    }

    /** Matérias fortes da pessoa que o aluno marcou como difíceis. */
    static List<String> ajudaEm(Perfil perfil, Pessoa pessoa) {
        return pessoa.strong().stream().filter(s -> perfil.levels().getOrDefault(s, 0) == 1).toList();
    }

    public List<Grupo> grupos(Perfil perfil) {
        Curso curso = catalogo.curso(perfil.courseId());
        boolean noite = periodos(perfil).contains(2);
        return List.of(
                new Grupo("curso-" + curso.id() + "-" + perfil.cycle(), curso.name() + " · " + perfil.cycle(),
                        "Quem quer o mesmo curso que você, na mesma prova."),
                new Grupo("redacao-" + perfil.cycle(), "Redação nota 1000", "Um tema por semana e correção entre colegas."),
                noite
                        ? new Grupo("turno-noite", "Estudo noturno", "Sessões juntos das 19h às 22h, com check-in no fim.")
                        : new Grupo("turno-manha", "Clube das 6h", "Quem começa cedo e quer companhia para manter o ritmo."),
                new Grupo("questoes-dia", "10 questões por dia", "Desafio diário do Banco de Questões, com ranking semanal."));
    }

    /** Uma pessoa de exemplo. {@code periods}: 0 manhã, 1 tarde, 2 noite. */
    public record Pessoa(String id, String name, String city, String track, int cycle, String courseId,
            List<Integer> periods, List<String> strong, List<String> weak, int streak, String bio) {
    }

    /** {@code helpsWith}: matérias em que a pessoa pode ajudar o aluno. */
    public record Combinacao(Pessoa person, int score, List<String> reasons, List<String> helpsWith) {
    }

    public record Grupo(String id, String name, String desc) {
    }
}
