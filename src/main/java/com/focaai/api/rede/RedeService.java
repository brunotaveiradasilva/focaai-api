package com.focaai.api.rede;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.comum.RecursoNaoEncontradoException;
import com.focaai.api.comum.RequisicaoInvalidaException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfis, busca e amizades. Amizade é mútua: um pede, o outro aceita. Pedir amizade a quem já
 * pediu a você aceita o pedido; desfazer serve para desfazer a amizade, cancelar ou recusar o pedido.
 */
@Service
public class RedeService {

    static final int MAX_BIO = 160;
    private static final int MAX_BUSCA = 20;

    // Relação do aluno logado com outro, como o app recebe.
    static final String EU = "self";
    static final String AMIGOS = "friends";
    static final String ENVIADO = "sent";
    static final String RECEBIDO = "received";

    private final JdbcClient banco;

    public RedeService(JdbcClient banco) {
        this.banco = banco;
    }

    @Transactional(readOnly = true)
    public PerfilPublico perfil(UUID eu, UUID id) {
        record Linha(Membro membro, String bio) {
        }
        Linha linha = banco.sql("SELECT id AS u_id, nome AS u_nome, foto AS u_foto, bio FROM usuario WHERE id = ?")
                .param(id).query((rs, n) -> new Linha(Membro.de(rs), rs.getString("bio"))).optional()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno não encontrado."));
        int amigos = banco.sql("SELECT count(*) FROM amizade WHERE (de = :id OR para = :id) AND aceita_em IS NOT NULL")
                .param("id", id).query(Integer.class).single();
        int posts = banco.sql("SELECT count(*) FROM post WHERE autor_id = ?").param(id).query(Integer.class).single();
        return new PerfilPublico(linha.membro(), linha.bio(), amigos, posts, relacao(eu, id));
    }

    @Transactional
    public PerfilPublico definirBio(UUID eu, String bio) {
        String texto = bio == null ? "" : bio.strip();
        if (texto.length() > MAX_BIO) throw new RequisicaoInvalidaException("A bio pode ter até " + MAX_BIO + " caracteres.");
        banco.sql("UPDATE usuario SET bio = ? WHERE id = ?").params(texto.isEmpty() ? null : texto, eu).update();
        return perfil(eu, eu);
    }

    /** Alunos cujo nome contém o texto, menos quem busca. */
    @Transactional(readOnly = true)
    public List<MembroComRelacao> buscar(UUID eu, String texto) {
        String busca = texto == null ? "" : texto.strip();
        if (busca.length() < 2) return List.of();
        String padrao = "%" + busca.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return banco.sql("""
                SELECT u.id AS u_id, u.nome AS u_nome, u.foto AS u_foto, a.de, a.aceita_em IS NOT NULL AS aceita
                FROM usuario u
                LEFT JOIN amizade a ON (a.de = :eu AND a.para = u.id) OR (a.para = :eu AND a.de = u.id)
                WHERE u.id <> :eu AND u.nome ILIKE :padrao
                ORDER BY u.nome
                LIMIT :limite
                """)
                .param("eu", eu).param("padrao", padrao).param("limite", MAX_BUSCA)
                .query((rs, n) -> new MembroComRelacao(Membro.de(rs),
                        relacao(eu, rs.getObject("de", UUID.class), rs.getBoolean("aceita"))))
                .list();
    }

    /** Amigos e pedidos pendentes, nos dois sentidos, dos mais recentes para os mais antigos. */
    @Transactional(readOnly = true)
    public Amigos amigos(UUID eu) {
        List<Membro> amigos = new ArrayList<>();
        List<Membro> recebidos = new ArrayList<>();
        List<Membro> enviados = new ArrayList<>();
        banco.sql("""
                SELECT u.id AS u_id, u.nome AS u_nome, u.foto AS u_foto, a.de, a.aceita_em IS NOT NULL AS aceita
                FROM amizade a JOIN usuario u ON u.id = CASE WHEN a.de = :eu THEN a.para ELSE a.de END
                WHERE a.de = :eu OR a.para = :eu
                ORDER BY coalesce(a.aceita_em, a.pedida_em) DESC
                """)
                .param("eu", eu)
                .query((rs, n) -> {
                    Membro membro = Membro.de(rs);
                    switch (relacao(eu, rs.getObject("de", UUID.class), rs.getBoolean("aceita"))) {
                        case AMIGOS -> amigos.add(membro);
                        case RECEBIDO -> recebidos.add(membro);
                        default -> enviados.add(membro);
                    }
                    return membro;
                })
                .list();
        return new Amigos(amigos, recebidos, enviados);
    }

    /** Pede amizade, ou aceita o pedido que a outra pessoa já tinha feito. Devolve a relação depois disso. */
    @Transactional
    public Relacao pedirAmizade(UUID eu, UUID outro) {
        if (eu.equals(outro)) throw new RequisicaoInvalidaException("Você não pode pedir amizade a si mesmo.");
        if (!existe(outro)) throw new RecursoNaoEncontradoException("Aluno não encontrado.");
        int aceitos = banco.sql("UPDATE amizade SET aceita_em = now() WHERE de = ? AND para = ? AND aceita_em IS NULL")
                .params(outro, eu).update();
        if (aceitos == 0) {
            banco.sql("INSERT INTO amizade (de, para) VALUES (?, ?) ON CONFLICT DO NOTHING").params(eu, outro).update();
        }
        return new Relacao(relacao(eu, outro));
    }

    /** Desfaz a amizade, cancela o pedido enviado ou recusa o recebido. */
    @Transactional
    public void desfazerAmizade(UUID eu, UUID outro) {
        banco.sql("DELETE FROM amizade WHERE (de = :eu AND para = :outro) OR (de = :outro AND para = :eu)")
                .param("eu", eu).param("outro", outro).update();
    }

    /** O próprio aluno ou um amigo: quem pode ver os posts de {@code autor}. */
    boolean podeVer(UUID eu, UUID autor) {
        String relacao = relacao(eu, autor);
        return EU.equals(relacao) || AMIGOS.equals(relacao);
    }

    boolean existe(UUID id) {
        return banco.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE id = ?)").param(id).query(Boolean.class).single();
    }

    String relacao(UUID eu, UUID outro) {
        if (eu.equals(outro)) return EU;
        return banco.sql("""
                SELECT de, aceita_em IS NOT NULL AS aceita FROM amizade
                WHERE (de = :eu AND para = :outro) OR (de = :outro AND para = :eu)
                """)
                .param("eu", eu).param("outro", outro)
                .query((rs, n) -> relacao(eu, rs.getObject("de", UUID.class), rs.getBoolean("aceita")))
                .optional().orElse(null);
    }

    /** {@code de}: quem pediu (nulo se não há pedido). */
    private static String relacao(UUID eu, UUID de, boolean aceita) {
        if (de == null) return null;
        if (aceita) return AMIGOS;
        return de.equals(eu) ? ENVIADO : RECEBIDO;
    }

    /**
     * {@code friendship}: {@code self} (o próprio aluno), {@code friends}, {@code sent} (pedido
     * enviado), {@code received} (pedido recebido) ou nulo.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PerfilPublico(Membro user, String bio, int friends, int posts, String friendship) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MembroComRelacao(Membro user, String friendship) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Relacao(String friendship) {
    }

    public record Amigos(List<Membro> friends, List<Membro> received, List<Membro> sent) {
    }
}
