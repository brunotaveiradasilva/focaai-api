package com.focaai.api.conteudo;

import java.util.List;
import java.util.Map;

import com.focaai.api.comum.RequisicaoInvalidaException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/** Conteúdo fixo, igual para todo mundo: aberto, sem login. */
@RestController
@RequestMapping("/api/conteudo")
public class ConteudoController {

    private final Catalogo catalogo;

    public ConteudoController(Catalogo catalogo) {
        this.catalogo = catalogo;
    }

    /** Tudo que o app precisa para montar onboarding, perfil e telas: carregado uma vez na abertura. */
    @GetMapping
    public ConteudoResumo resumo() {
        return new ConteudoResumo(
                catalogo.materias(),
                catalogo.areas(),
                catalogo.cursos(),
                catalogo.concorrencia(),
                catalogo.provas(),
                catalogo.vestibulares(),
                catalogo.ciclos(),
                catalogo.trilhas(),
                catalogo.topicos());
    }

    /** Roadmap de uma matéria para as provas informadas (ex.: {@code ?provas=enem,ufms}). */
    @GetMapping("/roteiros/{materia}")
    public List<TopicoNoRoteiro> roteiro(@PathVariable String materia, @RequestParam List<String> provas) {
        catalogo.materia(materia);
        if (provas.isEmpty()) throw new RequisicaoInvalidaException("Informe ao menos uma prova.");
        return catalogo.roteiro(materia, provas);
    }

    @GetMapping("/editais")
    public List<Edital> editais() {
        return catalogo.editais();
    }

    @GetMapping("/editais/{id}")
    public Edital edital(@PathVariable String id) {
        return catalogo.edital(id);
    }

    /** Assuntos de um edital numa matéria da taxonomia (ex.: {@code fil}), do mais pesado ao mais leve. */
    @GetMapping("/editais/{id}/topicos")
    public List<Edital.TopicoDoEdital> topicosDoEdital(@PathVariable String id, @RequestParam String materia) {
        return catalogo.topicosDoEdital(id, materia);
    }

    /** Calendário completo e estrutura de uma edição ({@code ufms-2027} traz também os pesos por curso). */
    @GetMapping("/provas/{id}")
    public JsonNode detalhesDaProva(@PathVariable String id) {
        return catalogo.detalhesDaProva(id);
    }

    public record ConteudoResumo(
            List<Materia> subjects,
            List<AreaEnem> areas,
            List<Curso> courses,
            Map<String, Concorrencia> tiers,
            List<Prova> exams,
            List<Vestibular> vestibulares,
            List<Integer> cycles,
            Map<String, String> trackLabels,
            List<Topico> topics) {
    }
}
