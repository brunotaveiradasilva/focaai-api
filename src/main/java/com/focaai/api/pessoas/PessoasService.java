package com.focaai.api.pessoas;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focaai.api.aluno.AlunoService;
import com.focaai.api.aluno.Perfil;
import com.focaai.api.comum.RecursoNaoEncontradoException;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tela Pessoas: parceiros sugeridos, pedidos de conexão e grupos. */
@Service
public class PessoasService {

    private final Pessoas pessoas;
    private final AlunoService alunos;
    private final JdbcClient banco;

    public PessoasService(Pessoas pessoas, AlunoService alunos, JdbcClient banco) {
        this.pessoas = pessoas;
        this.alunos = alunos;
        this.banco = banco;
    }

    @Transactional(readOnly = true)
    public TelaPessoas tela(UUID usuarioId) {
        Perfil perfil = alunos.dados(usuarioId).perfil();
        Map<String, String> conexoes = conexoes(usuarioId);
        Set<String> meusGrupos = grupos(usuarioId);
        List<Sugestao> sugestoes = pessoas.combinar(perfil).stream()
                .map(c -> new Sugestao(c.person(), c.score(), c.reasons(), c.helpsWith(), conexoes.get(c.person().id())))
                .toList();
        List<GrupoDoAluno> grupos = pessoas.grupos(perfil).stream()
                .map(g -> new GrupoDoAluno(g.id(), g.name(), g.desc(), meusGrupos.contains(g.id())))
                .toList();
        return new TelaPessoas(sugestoes, grupos);
    }

    /** Pede conexão; fica pendente até a outra pessoa aceitar. */
    @Transactional
    public void conectar(UUID usuarioId, String pessoaId) {
        if (pessoas.exemplos().stream().noneMatch(p -> p.id().equals(pessoaId))) {
            throw new RecursoNaoEncontradoException("Pessoa não encontrada.");
        }
        banco.sql("INSERT INTO conexao (usuario_id, pessoa_id, status) VALUES (?, ?, 'pending') ON CONFLICT DO NOTHING")
                .params(usuarioId, pessoaId).update();
    }

    @Transactional
    public void entrarNoGrupo(UUID usuarioId, String grupoId) {
        Perfil perfil = alunos.dados(usuarioId).perfil();
        if (pessoas.grupos(perfil).stream().noneMatch(g -> g.id().equals(grupoId))) {
            throw new RecursoNaoEncontradoException("Grupo não encontrado.");
        }
        banco.sql("INSERT INTO grupo_membro (usuario_id, grupo_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(usuarioId, grupoId).update();
    }

    @Transactional
    public void sairDoGrupo(UUID usuarioId, String grupoId) {
        banco.sql("DELETE FROM grupo_membro WHERE usuario_id = ? AND grupo_id = ?").params(usuarioId, grupoId).update();
    }

    @EventListener
    public void aoZerar(AlunoService.DadosZerados evento) {
        banco.sql("DELETE FROM conexao WHERE usuario_id = ?").param(evento.usuarioId()).update();
        banco.sql("DELETE FROM grupo_membro WHERE usuario_id = ?").param(evento.usuarioId()).update();
    }

    private Map<String, String> conexoes(UUID usuarioId) {
        Map<String, String> conexoes = new LinkedHashMap<>();
        banco.sql("SELECT pessoa_id, status FROM conexao WHERE usuario_id = ?").param(usuarioId)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2))).list()
                .forEach(e -> conexoes.put(e.getKey(), e.getValue()));
        return conexoes;
    }

    private Set<String> grupos(UUID usuarioId) {
        return new HashSet<>(banco.sql("SELECT grupo_id FROM grupo_membro WHERE usuario_id = ?").param(usuarioId)
                .query(String.class).list());
    }

    public record TelaPessoas(List<Sugestao> matches, List<GrupoDoAluno> groups) {
    }

    /** {@code connection}: {@code pending}, {@code connected} ou nulo (ainda não pediu). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Sugestao(Pessoas.Pessoa person, int score, List<String> reasons, List<String> helpsWith, String connection) {
    }

    public record GrupoDoAluno(String id, String name, String desc, boolean joined) {
    }
}
