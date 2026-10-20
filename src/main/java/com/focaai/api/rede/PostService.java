package com.focaai.api.rede;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.comum.ProibidoException;
import com.focaai.api.comum.RecursoNaoEncontradoException;
import com.focaai.api.comum.RequisicaoInvalidaException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posts e comentários. Um post só aparece para o autor e os amigos dele; só eles comentam. */
@Service
public class PostService {

    static final int MAX_POST = 2000;
    static final int MAX_COMENTARIO = 500;
    static final int POR_PAGINA = 20;
    private static final int MAX_COMENTARIOS = 200;

    private static final String POSTS = """
            SELECT p.id, p.texto, p.criado_em, p.imagem_id, u.id AS u_id, u.nome AS u_nome, u.foto AS u_foto,
                   (SELECT count(*) FROM comentario c WHERE c.post_id = p.id) AS comentarios
            FROM post p JOIN usuario u ON u.id = p.autor_id
            """;

    private final RedeService rede;
    private final ImagemService imagens;
    private final NotificacaoService notificacoes;
    private final JdbcClient banco;

    public PostService(RedeService rede, ImagemService imagens, NotificacaoService notificacoes, JdbcClient banco) {
        this.rede = rede;
        this.imagens = imagens;
        this.notificacoes = notificacoes;
        this.banco = banco;
    }

    /** Posts do aluno e dos amigos, do mais novo para o mais antigo. {@code antes}: o {@code next} da página anterior. */
    @Transactional(readOnly = true)
    public Pagina feed(UUID eu, Long antes) {
        return pagina(eu, banco.sql(POSTS + """
                WHERE (p.autor_id = :eu OR p.autor_id IN (
                        SELECT CASE WHEN de = :eu THEN para ELSE de END FROM amizade
                        WHERE (de = :eu OR para = :eu) AND aceita_em IS NOT NULL))
                  AND p.id < :antes
                ORDER BY p.id DESC
                LIMIT :limite
                """).param("eu", eu).param("antes", antes == null ? Long.MAX_VALUE : antes).param("limite", POR_PAGINA));
    }

    @Transactional(readOnly = true)
    public Pagina doAutor(UUID eu, UUID autor, Long antes) {
        if (!rede.existe(autor)) throw new RecursoNaoEncontradoException("Aluno não encontrado.");
        if (!rede.podeVer(eu, autor)) throw new ProibidoException("Só os amigos veem os posts deste aluno.");
        return pagina(eu, banco.sql(POSTS + """
                WHERE p.autor_id = :autor AND p.id < :antes
                ORDER BY p.id DESC
                LIMIT :limite
                """).param("autor", autor).param("antes", antes == null ? Long.MAX_VALUE : antes).param("limite", POR_PAGINA));
    }

    /** {@code foto}: uma imagem enviada por este aluno ({@code /api/imagens/...}), ou nula. Com foto, o texto pode ficar vazio. */
    @Transactional
    public Post publicar(UUID eu, String texto, String foto) {
        UUID imagem = foto == null ? null : imagens.minha(eu, foto);
        String limpo = imagem != null && (texto == null || texto.isBlank()) ? "" : texto(texto, MAX_POST, "O post");
        long id = banco.sql("INSERT INTO post (autor_id, texto, imagem_id) VALUES (?, ?, ?) RETURNING id")
                .params(eu, limpo, imagem).query(Long.class).single();
        notificacoes.aoPublicar(eu, id, limpo.isEmpty() ? null : limpo);
        return post(eu, id);
    }

    /** O post com os comentários, do mais antigo para o mais novo. */
    @Transactional(readOnly = true)
    public PostAberto abrir(UUID eu, long id) {
        Post post = visivel(eu, id);
        List<Comentario> comentarios = banco.sql("""
                SELECT c.id, c.texto, c.criado_em, u.id AS u_id, u.nome AS u_nome, u.foto AS u_foto
                FROM comentario c JOIN usuario u ON u.id = c.autor_id
                WHERE c.post_id = :post
                ORDER BY c.id
                LIMIT :limite
                """).param("post", id).param("limite", MAX_COMENTARIOS)
                .query((rs, n) -> comentario(rs, eu, post)).list();
        return new PostAberto(post, comentarios);
    }

    @Transactional
    public void apagar(UUID eu, long id) {
        if (!autor(id).equals(eu)) throw new ProibidoException("Só quem escreveu pode apagar o post.");
        banco.sql("DELETE FROM post WHERE id = ?").param(id).update();
    }

    @Transactional
    public Comentario comentar(UUID eu, long postId, String texto) {
        Post post = visivel(eu, postId);
        String limpo = texto(texto, MAX_COMENTARIO, "O comentário");
        long id = banco.sql("INSERT INTO comentario (post_id, autor_id, texto) VALUES (?, ?, ?) RETURNING id")
                .params(postId, eu, limpo).query(Long.class).single();
        notificacoes.aoComentar(eu, post.author().id(), postId, limpo);
        return banco.sql("""
                SELECT c.id, c.texto, c.criado_em, u.id AS u_id, u.nome AS u_nome, u.foto AS u_foto
                FROM comentario c JOIN usuario u ON u.id = c.autor_id
                WHERE c.id = ?
                """).param(id).query((rs, n) -> comentario(rs, eu, post)).single();
    }

    /** Quem comentou apaga o comentário; o autor do post apaga qualquer comentário nele. */
    @Transactional
    public void apagarComentario(UUID eu, long postId, long comentarioId) {
        UUID autorDoPost = autor(postId);
        UUID autorDoComentario = banco.sql("SELECT autor_id FROM comentario WHERE id = ? AND post_id = ?")
                .params(comentarioId, postId).query(UUID.class).optional()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comentário não encontrado."));
        if (!eu.equals(autorDoComentario) && !eu.equals(autorDoPost)) {
            throw new ProibidoException("Só quem escreveu ou o autor do post pode apagar o comentário.");
        }
        banco.sql("DELETE FROM comentario WHERE id = ?").param(comentarioId).update();
    }

    /** O post, se o aluno pode vê-lo. Quem não pode recebe "não encontrado", como se o post não existisse. */
    private Post visivel(UUID eu, long id) {
        Post post = post(eu, id);
        if (!rede.podeVer(eu, post.author().id())) throw new RecursoNaoEncontradoException("Post não encontrado.");
        return post;
    }

    private Post post(UUID eu, long id) {
        return banco.sql(POSTS + "WHERE p.id = ?").param(id).query((rs, n) -> post(rs, eu)).optional()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Post não encontrado."));
    }

    private UUID autor(long postId) {
        return banco.sql("SELECT autor_id FROM post WHERE id = ?").param(postId).query(UUID.class).optional()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Post não encontrado."));
    }

    private Pagina pagina(UUID eu, JdbcClient.StatementSpec consulta) {
        List<Post> posts = consulta.query((rs, n) -> post(rs, eu)).list();
        Long proxima = posts.size() == POR_PAGINA ? posts.getLast().id() : null;
        return new Pagina(posts, proxima);
    }

    private static Post post(ResultSet rs, UUID eu) throws SQLException {
        Membro autor = Membro.de(rs);
        return new Post(rs.getLong("id"), autor, rs.getString("texto"), rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
                rs.getInt("comentarios"), autor.id().equals(eu), ImagemService.url(rs.getObject("imagem_id", UUID.class)));
    }

    private static Comentario comentario(ResultSet rs, UUID eu, Post post) throws SQLException {
        Membro autor = Membro.de(rs);
        return new Comentario(rs.getLong("id"), autor, rs.getString("texto"),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant(), autor.id().equals(eu) || post.mine());
    }

    private static String texto(String texto, int maximo, String oQue) {
        String limpo = texto == null ? "" : texto.strip();
        if (limpo.isEmpty()) throw new RequisicaoInvalidaException(oQue + " está vazio.");
        if (limpo.length() > maximo) throw new RequisicaoInvalidaException(oQue + " pode ter até " + maximo + " caracteres.");
        return limpo;
    }

    /** {@code mine}: o aluno logado escreveu (e pode apagar). {@code image}: endereço da foto na API, ou nulo. */
    public record Post(long id, Membro author, String text, Instant createdAt, int comments, boolean mine, String image) {
    }

    /** {@code canDelete}: o aluno logado escreveu o comentário ou o post. */
    public record Comentario(long id, Membro author, String text, Instant createdAt, boolean canDelete) {
    }

    public record PostAberto(Post post, List<Comentario> comments) {
    }

    /** {@code next}: passe em {@code antes} para a página seguinte; nulo quando acabou. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Pagina(List<Post> posts, Long next) {
    }
}
