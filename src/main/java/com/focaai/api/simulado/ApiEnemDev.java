package com.focaai.api.simulado;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Provas reais do ENEM de 2009 a 2023 pela api pública <a href="https://api.enem.dev">api.enem.dev</a>
 * (domínio público). Porte do {@code enem.ts} do app.
 */
@Component
public class ApiEnemDev implements FonteEnem {

    private static final int POR_PAGINA = 50;
    // A API aceita mais ou menos uma chamada sem cache por segundo; as chamadas esperam a vez.
    private static final long INTERVALO_MS = 1100;

    private final RestClient http;
    private long proximaVez = 0;

    public ApiEnemDev(@Value("${app.enem.url:https://api.enem.dev/v1}") String url) {
        this.http = RestClient.builder().baseUrl(url).build();
    }

    @Override
    public List<QuestaoEnem> baixar(int ano, String lingua) {
        List<JsonNode> brutas = new ArrayList<>();
        for (int offset = 0; offset < 200; offset += POR_PAGINA) {
            JsonNode pagina = buscar("/exams/{ano}/questions?limit={limite}&offset={offset}&language={lingua}",
                    ano, POR_PAGINA, offset, lingua, 0);
            pagina.get("questions").forEach(brutas::add);
            JsonNode meta = pagina.get("metadata");
            if (meta != null && !meta.path("hasMore").asBoolean(true)) break;
        }
        return converter(ano, lingua, brutas);
    }

    private JsonNode buscar(String caminho, int ano, int limite, int offset, String lingua, int tentativa) {
        esperarAVez();
        try {
            return http.get().uri(caminho, ano, limite, offset, lingua).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            if (tentativa >= 3) throw e;
            String depois = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
            dormir(depois != null && depois.matches("\\d+") ? Long.parseLong(depois) * 1000 : 2000);
            return buscar(caminho, ano, limite, offset, lingua, tentativa + 1);
        }
    }

    private synchronized void esperarAVez() {
        long agora = System.currentTimeMillis();
        long espera = Math.max(0, proximaVez - agora);
        proximaVez = agora + espera + INTERVALO_MS;
        dormir(espera);
    }

    /**
     * Converte e filtra: uma questão por número, só na língua estrangeira escolhida, sem as que citam
     * imagens que a API não conseguiu extrair e sem as que não têm gabarito.
     */
    static List<QuestaoEnem> converter(int ano, String lingua, List<JsonNode> brutas) {
        Set<Integer> vistos = new HashSet<>();
        List<QuestaoEnem> questoes = new ArrayList<>();
        for (JsonNode q : brutas) {
            int numero = q.get("index").asInt();
            if (vistos.contains(numero)) continue;
            String linguaDaQuestao = texto(q, "language");
            if (linguaDaQuestao != null && !linguaDaQuestao.equals(lingua)) continue;
            if (q.toString().contains("broken-image")) continue;
            String gabarito = texto(q, "correctAlternative");
            JsonNode alternativas = q.get("alternatives");
            if (gabarito == null || alternativas == null || alternativas.size() < 2) continue;
            vistos.add(numero);

            List<String> arquivos = new ArrayList<>();
            if (q.get("files") != null) q.get("files").forEach(f -> arquivos.add(f.asString()));
            List<QuestaoEnem.Alternativa> alts = new ArrayList<>();
            alternativas.forEach(a -> alts.add(new QuestaoEnem.Alternativa(a.get("letter").asString(), texto(a, "text"), texto(a, "file"))));
            questoes.add(new QuestaoEnem(ano + "-" + numero, ano, numero, areaDoNumero(ano, numero),
                    valorOuVazio(q, "context"), valorOuVazio(q, "alternativesIntroduction"), arquivos, alts, gabarito));
        }
        return questoes;
    }

    /**
     * O campo {@code discipline} da API vem errado em algumas questões de cada prova, então a área sai
     * do número da questão, seguindo a ordem oficial de cada edição.
     */
    static String areaDoNumero(int ano, int numero) {
        int bloco = Math.min(3, (numero - 1) / 45);
        String[] ordem = ano >= 2017
                ? new String[] {"linguagens", "humanas", "natureza", "matematica"}
                : new String[] {"humanas", "natureza", "linguagens", "matematica"};
        return ordem[bloco];
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode v = no.get(campo);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String valorOuVazio(JsonNode no, String campo) {
        String v = texto(no, campo);
        return v == null ? "" : v;
    }

    private static void dormir(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
