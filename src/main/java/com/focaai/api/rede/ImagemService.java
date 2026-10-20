package com.focaai.api.rede;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.focaai.api.comum.RequisicaoInvalidaException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fotos de perfil e de posts, guardadas no banco. O app reduz e comprime antes de enviar; aqui só se
 * confere o tipo (pelos primeiros bytes, não pelo que o app diz) e o tamanho.
 */
@Service
public class ImagemService {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern URL = Pattern.compile("^/api/imagens/([0-9a-fA-F-]{36})$");

    private final JdbcClient banco;

    public ImagemService(JdbcClient banco) {
        this.banco = banco;
    }

    @Transactional
    public Enviada salvar(UUID eu, String base64) {
        byte[] dados;
        try {
            dados = Base64.getDecoder().decode(base64 == null ? "" : base64.replaceFirst("^data:[^,]*,", ""));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("A imagem não veio num formato válido.");
        }
        if (dados.length == 0) throw new RequisicaoInvalidaException("A imagem está vazia.");
        if (dados.length > MAX_BYTES) throw new RequisicaoInvalidaException("A imagem pode ter até 2 MB.");
        String tipo = tipo(dados);
        if (tipo == null) throw new RequisicaoInvalidaException("Envie uma imagem JPEG, PNG ou WebP.");
        UUID id = UUID.randomUUID();
        banco.sql("INSERT INTO imagem (id, usuario_id, tipo, dados) VALUES (?, ?, ?, ?)").params(id, eu, tipo, dados).update();
        return new Enviada(id, url(id));
    }

    @Transactional(readOnly = true)
    public Optional<Imagem> ler(UUID id) {
        return banco.sql("SELECT tipo, dados FROM imagem WHERE id = ?").param(id)
                .query((rs, n) -> new Imagem(rs.getString("tipo"), rs.getBytes("dados"))).optional();
    }

    /** Id da imagem num endereço {@code /api/imagens/{id}}, se for uma imagem enviada por este aluno. */
    UUID minha(UUID eu, String url) {
        UUID id = idDe(url);
        boolean minha = id != null && banco.sql("SELECT EXISTS (SELECT 1 FROM imagem WHERE id = ? AND usuario_id = ?)")
                .params(id, eu).query(Boolean.class).single();
        if (!minha) throw new RequisicaoInvalidaException("Imagem não encontrada. Envie a foto de novo.");
        return id;
    }

    static String url(UUID id) {
        return id == null ? null : "/api/imagens/" + id;
    }

    static UUID idDe(String url) {
        Matcher m = url == null ? null : URL.matcher(url);
        return m != null && m.matches() ? UUID.fromString(m.group(1)) : null;
    }

    private static final Map<String, byte[]> ASSINATURAS = Map.of(
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});

    private static String tipo(byte[] dados) {
        for (var assinatura : ASSINATURAS.entrySet()) {
            if (comeca(dados, 0, assinatura.getValue())) return assinatura.getKey();
        }
        // WebP: "RIFF", tamanho, "WEBP".
        if (comeca(dados, 0, "RIFF".getBytes()) && comeca(dados, 8, "WEBP".getBytes())) return "image/webp";
        return null;
    }

    private static boolean comeca(byte[] dados, int de, byte[] prefixo) {
        if (dados.length < de + prefixo.length) return false;
        for (int i = 0; i < prefixo.length; i++) {
            if (dados[de + i] != prefixo[i]) return false;
        }
        return true;
    }

    /** {@code url}: o endereço da imagem na API, relativo (o app junta com o endereço da API). */
    public record Enviada(UUID id, String url) {
    }

    public record Imagem(String tipo, byte[] dados) {
    }
}
