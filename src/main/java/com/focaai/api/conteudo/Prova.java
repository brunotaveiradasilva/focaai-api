package com.focaai.api.conteudo;

import java.util.List;

/**
 * Uma edição de prova com as datas confirmadas pelos organizadores. {@code cycle} é o ano em que a
 * prova é aplicada (a Fuvest 2027 é aplicada em 2026).
 */
public record Prova(
        String id,
        String name,
        String kind,
        int cycle,
        List<Fase> phases,
        String source,
        String checkedAt) {

    /** Uma fase ou dia de prova, com as datas em ISO (dia do calendário local). */
    public record Fase(String label, List<String> dates) {
    }
}
