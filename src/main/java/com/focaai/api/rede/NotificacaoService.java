package com.focaai.api.rede;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A atividade do aluno: quem pediu ou aceitou amizade, quem comentou nos seus posts, o que os
 * amigos postaram e o lembrete de fazer simulado. As outras partes da rede chamam os métodos
 * {@code ao...} quando a coisa acontece; o lembrete nasce ao abrir a atividade.
 */
@Service
public class NotificacaoService {

    static final String PEDIDO = "friend_request";
    static final String ACEITE = "friend_accepted";
    static final String COMENTARIO = "comment";
    static final String POST = "post";
    static final String LEMBRETE = "simulado_reminder";

    private static final int MAX_LISTA = 50;
    private static final int MAX_TRECHO = 80;
    // Lembra de fazer simulado depois de tantos dias sem nenhum, e no máximo uma vez nesse intervalo.
    static final int DIAS_SEM_SIMULADO = 7;
    // Conta nova ainda está conhecendo o app: o lembrete só vem depois disso.
    static final int DIAS_DE_CONTA_NOVA = 3;

    private final JdbcClient banco;

    public NotificacaoService(JdbcClient banco) {
        this.banco = banco;
    }

    void aoPedirAmizade(UUID de, UUID para) {
        criar(para, PEDIDO, de, null, null);
    }

    /** O pedido aceito sai da atividade de quem aceitou, e quem pediu fica sabendo. */
    void aoAceitarAmizade(UUID quemAceitou, UUID quemPediu) {
        banco.sql("DELETE FROM notificacao WHERE usuario_id = ? AND ator_id = ? AND tipo = ?")
                .params(quemAceitou, quemPediu, PEDIDO).update();
        criar(quemPediu, ACEITE, quemAceitou, null, null);
    }

    /** Desfazer, cancelar ou recusar: some o que restou desse pedido para os dois. */
    void aoDesfazerAmizade(UUID a, UUID b) {
        banco.sql("""
                DELETE FROM notificacao WHERE tipo IN (:tipos)
                  AND ((usuario_id = :a AND ator_id = :b) OR (usuario_id = :b AND ator_id = :a))
                """).param("tipos", List.of(PEDIDO, ACEITE)).param("a", a).param("b", b).update();
    }

    /** Avisa todos os amigos do autor. */
    void aoPublicar(UUID autor, long postId, String texto) {
        banco.sql("""
                INSERT INTO notificacao (usuario_id, tipo, ator_id, post_id, trecho)
                SELECT CASE WHEN de = :autor THEN para ELSE de END, :tipo, :autor, :post, :trecho
                FROM amizade WHERE (de = :autor OR para = :autor) AND aceita_em IS NOT NULL
                """).param("autor", autor).param("tipo", POST).param("post", postId).param("trecho", trecho(texto)).update();
    }

    /** Avisa o autor do post, a não ser que ele mesmo tenha comentado. */
    void aoComentar(UUID quem, UUID autorDoPost, long postId, String texto) {
        if (quem.equals(autorDoPost)) return;
        criar(autorDoPost, COMENTARIO, quem, postId, trecho(texto));
    }

    @Transactional
    public Atividade atividade(UUID eu) {
        lembrarSimulado(eu);
        List<Notificacao> itens = banco.sql("""
                SELECT n.id, n.tipo, n.post_id, n.trecho, n.criada_em, n.lida_em IS NOT NULL AS lida,
                       u.id AS u_id, u.nome AS u_nome, u.foto AS u_foto
                FROM notificacao n LEFT JOIN usuario u ON u.id = n.ator_id
                WHERE n.usuario_id = :eu
                ORDER BY n.id DESC
                LIMIT :limite
                """).param("eu", eu).param("limite", MAX_LISTA).query(NotificacaoService::notificacao).list();
        return new Atividade(itens, naoLidas(eu));
    }

    /** Só o número, para o sininho: leve o bastante para o app perguntar de tempos em tempos. */
    @Transactional
    public NaoLidas naoLidasAgora(UUID eu) {
        lembrarSimulado(eu);
        return new NaoLidas(naoLidas(eu));
    }

    @Transactional
    public void marcarLidas(UUID eu) {
        banco.sql("UPDATE notificacao SET lida_em = now() WHERE usuario_id = ? AND lida_em IS NULL").param(eu).update();
    }

    private int naoLidas(UUID eu) {
        return banco.sql("SELECT count(*) FROM notificacao WHERE usuario_id = ? AND lida_em IS NULL").param(eu)
                .query(Integer.class).single();
    }

    /** Uma semana sem simulado (nem começado) vira um lembrete, se não teve outro nessa semana. */
    private void lembrarSimulado(UUID eu) {
        banco.sql("""
                INSERT INTO notificacao (usuario_id, tipo)
                SELECT :eu, :tipo FROM usuario u
                WHERE u.id = :eu
                  AND u.criado_em < now() - make_interval(days => :novo)
                  AND NOT EXISTS (SELECT 1 FROM simulado s
                                  WHERE s.usuario_id = :eu AND s.criado_em > now() - make_interval(days => :dias))
                  AND NOT EXISTS (SELECT 1 FROM notificacao n
                                  WHERE n.usuario_id = :eu AND n.tipo = :tipo AND n.criada_em > now() - make_interval(days => :dias))
                """).param("eu", eu).param("tipo", LEMBRETE).param("novo", DIAS_DE_CONTA_NOVA).param("dias", DIAS_SEM_SIMULADO)
                .update();
    }

    private void criar(UUID para, String tipo, UUID ator, Long postId, String trecho) {
        banco.sql("INSERT INTO notificacao (usuario_id, tipo, ator_id, post_id, trecho) VALUES (?, ?, ?, ?, ?)")
                .params(para, tipo, ator, postId, trecho).update();
    }

    private static String trecho(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String limpo = texto.strip().replaceAll("\\s+", " ");
        return limpo.length() <= MAX_TRECHO ? limpo : limpo.substring(0, MAX_TRECHO - 1) + "…";
    }

    private static Notificacao notificacao(ResultSet rs, int n) throws SQLException {
        Membro ator = rs.getObject("u_id", UUID.class) == null ? null : Membro.de(rs);
        long post = rs.getLong("post_id");
        return new Notificacao(rs.getLong("id"), rs.getString("tipo"), ator, rs.wasNull() ? null : post, rs.getString("trecho"),
                rs.getObject("criada_em", OffsetDateTime.class).toInstant(), rs.getBoolean("lida"));
    }

    /**
     * {@code type}: {@code friend_request}, {@code friend_accepted}, {@code comment}, {@code post}
     * ou {@code simulado_reminder}. {@code actor}: quem fez (nulo no lembrete). {@code excerpt}: o
     * começo do comentário ou do post.
     */
    public record Notificacao(long id, String type, Membro actor, Long postId, String excerpt, Instant createdAt, boolean read) {
    }

    public record Atividade(List<Notificacao> items, int unread) {
    }

    public record NaoLidas(int unread) {
    }
}
