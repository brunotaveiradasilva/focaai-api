package com.focaai.api.simulado;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Banco de questões reais do ENEM. Cada prova é baixada uma vez, guardada no PostgreSQL e mantida em
 * memória; daí em diante, montar um simulado não depende da api.enem.dev.
 */
@Component
public class BancoDeQuestoes {

    public static final int PRIMEIRO_ANO = 2009;
    public static final int ULTIMO_ANO = 2023;
    public static final int RECENTES_DESDE = 2019;
    static final List<String> LINGUAS = List.of("ingles", "espanhol");

    private static final Logger log = LoggerFactory.getLogger(BancoDeQuestoes.class);

    private final FonteEnem fonte;
    private final JdbcClient banco;
    private final ObjectMapper json;
    private final boolean aquecer;
    private final Map<String, List<QuestaoEnem>> memoria = new ConcurrentHashMap<>();
    private final Map<String, Object> travas = new ConcurrentHashMap<>();

    public BancoDeQuestoes(FonteEnem fonte, JdbcClient banco, ObjectMapper json,
            @Value("${app.enem.aquecer-cache:true}") boolean aquecer) {
        this.fonte = fonte;
        this.banco = banco;
        this.json = json;
        this.aquecer = aquecer;
    }

    /** Baixa em segundo plano as provas que ainda faltam, para o primeiro simulado de cada aluno ser rápido. */
    @EventListener(ApplicationReadyEvent.class)
    public void aquecerCache() {
        if (!aquecer) return;
        Thread.ofVirtual().name("aquecer-enem").start(() -> {
            for (String lingua : LINGUAS) {
                for (int ano = ULTIMO_ANO; ano >= PRIMEIRO_ANO; ano--) {
                    try {
                        prova(ano, lingua);
                    } catch (RuntimeException e) {
                        log.warn("Não deu pra baixar a prova do ENEM {} ({}): {}", ano, lingua, e.getMessage());
                    }
                }
            }
            log.info("Provas do ENEM em cache: {}", memoria.size());
        });
    }

    /** Questões de uma prova, do cache se já tiver sido baixada. */
    public List<QuestaoEnem> prova(int ano, String lingua) {
        String chave = ano + ":" + lingua;
        List<QuestaoEnem> naMemoria = memoria.get(chave);
        if (naMemoria != null) return naMemoria;
        synchronized (travas.computeIfAbsent(chave, k -> new Object())) {
            naMemoria = memoria.get(chave);
            if (naMemoria != null) return naMemoria;
            List<QuestaoEnem> questoes = doBanco(ano, lingua);
            if (questoes == null) {
                questoes = fonte.baixar(ano, lingua);
                banco.sql("INSERT INTO prova_enem (ano, lingua, questoes) VALUES (?, ?, ?::jsonb) ON CONFLICT DO NOTHING")
                        .params(ano, lingua, json.writeValueAsString(questoes))
                        .update();
            }
            memoria.put(chave, questoes);
            return questoes;
        }
    }

    private List<QuestaoEnem> doBanco(int ano, String lingua) {
        return banco.sql("SELECT questoes::text FROM prova_enem WHERE ano = ? AND lingua = ?")
                .params(ano, lingua)
                .query(String.class)
                .optional()
                .map(texto -> json.readValue(texto, new TypeReference<List<QuestaoEnem>>() { }))
                .orElse(null);
    }

    private Set<Integer> anosEmCache(String lingua, List<Integer> anos) {
        Set<Integer> emCache = new HashSet<>();
        for (int ano : anos) if (memoria.containsKey(ano + ":" + lingua)) emCache.add(ano);
        emCache.addAll(banco.sql("SELECT ano FROM prova_enem WHERE lingua = ?").param(lingua).query(Integer.class).list());
        emCache.retainAll(anos);
        return emCache;
    }

    /**
     * Monta um simulado: a mesma quantidade de questões por área (a sobra vai para as primeiras), na
     * ordem de áreas da prova de verdade. Questões que o aluno já viu ({@code vistas}) só entram se
     * faltarem inéditas. Provas já baixadas não custam nada; baixa ao menos uma nova para variar até
     * existirem duas em cache, e mais só se ainda faltar questão.
     */
    public List<QuestaoEnem> montarSimulado(List<String> areas, int quantidade, boolean soRecentes, String lingua,
            Set<String> vistas) {
        int desde = soRecentes ? RECENTES_DESDE : PRIMEIRO_ANO;
        List<Integer> anos = new ArrayList<>();
        for (int ano = desde; ano <= ULTIMO_ANO; ano++) anos.add(ano);

        Map<String, Integer> porArea = new LinkedHashMap<>();
        for (int i = 0; i < areas.size(); i++) {
            porArea.put(areas.get(i), quantidade / areas.size() + (i < quantidade % areas.size() ? 1 : 0));
        }

        Set<Integer> emCache = anosEmCache(lingua, anos);
        List<Integer> jaBaixados = new ArrayList<>(emCache);
        List<Integer> novos = new ArrayList<>(anos);
        novos.removeAll(emCache);
        Collections.shuffle(jaBaixados);
        Collections.shuffle(novos);
        List<Integer> ordem = new ArrayList<>(jaBaixados);
        ordem.addAll(novos);

        Map<String, List<QuestaoEnem>> reserva = new LinkedHashMap<>();
        areas.forEach(a -> reserva.put(a, new ArrayList<>()));
        int baixados = 0;
        RuntimeException falha = null;
        for (int ano : ordem) {
            boolean jaEstava = emCache.contains(ano);
            if (!jaEstava && bastam(reserva, porArea, vistas) && (emCache.size() >= 2 || baixados >= 1)) break;
            List<QuestaoEnem> questoes;
            try {
                questoes = prova(ano, lingua);
            } catch (RuntimeException e) {
                // Uma prova que não baixa não derruba o simulado: ele sai com as outras.
                log.warn("Prova do ENEM {} ({}) indisponível para o simulado: {}", ano, lingua, e.getMessage());
                falha = e;
                continue;
            }
            for (QuestaoEnem q : questoes) {
                List<QuestaoEnem> daArea = reserva.get(q.area());
                if (daArea != null) daArea.add(q);
            }
            if (!jaEstava) baixados++;
        }
        // Nada veio de prova nenhuma: o problema é a fonte, não o pedido.
        if (falha != null && reserva.values().stream().allMatch(List::isEmpty)) throw falha;

        List<QuestaoEnem> simulado = new ArrayList<>();
        for (String area : areas) {
            List<QuestaoEnem> ineditas = new ArrayList<>();
            List<QuestaoEnem> repetidas = new ArrayList<>();
            for (QuestaoEnem q : reserva.get(area)) (vistas.contains(q.key()) ? repetidas : ineditas).add(q);
            Collections.shuffle(ineditas);
            Collections.shuffle(repetidas);
            ineditas.addAll(repetidas);
            simulado.addAll(ineditas.subList(0, Math.min(porArea.get(area), ineditas.size())));
        }
        return simulado;
    }

    private static boolean bastam(Map<String, List<QuestaoEnem>> reserva, Map<String, Integer> porArea, Set<String> vistas) {
        return porArea.entrySet().stream().allMatch(e ->
                reserva.get(e.getKey()).stream().filter(q -> !vistas.contains(q.key())).count() >= e.getValue());
    }
}
