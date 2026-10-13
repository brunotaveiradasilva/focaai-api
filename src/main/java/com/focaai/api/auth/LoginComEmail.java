package com.focaai.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

import com.focaai.api.auth.CodigoDeEmail.Finalidade;
import com.focaai.api.comum.ConflitoException;
import com.focaai.api.comum.MuitasTentativasException;
import com.focaai.api.comum.RequisicaoInvalidaException;
import com.focaai.api.email.EnviadorDeEmail;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conta com e-mail e senha. A conta só é criada depois de a pessoa digitar o código que chegou no
 * e-mail; assim todo e-mail guardado é de quem usa a conta, e o mesmo e-mail no Google cai na mesma conta.
 */
@Service
public class LoginComEmail {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    /** Limite do bcrypt: o que passa disso seria ignorado (ou recusado pelo Spring). */
    private static final int BYTES_MAXIMOS_DA_SENHA = 72;

    private final UsuarioRepository usuarios;
    private final CodigoDeEmailRepository codigos;
    private final SessaoRepository sessoes;
    private final PasswordEncoder senhas;
    private final EnviadorDeEmail email;
    private final AuthService auth;
    /** Conferido quando o e-mail não tem senha: a resposta demora o mesmo que uma senha errada. */
    private final String hashDeNenhumaSenha;

    public LoginComEmail(UsuarioRepository usuarios, CodigoDeEmailRepository codigos, SessaoRepository sessoes,
            PasswordEncoder senhas, EnviadorDeEmail email, AuthService auth) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.sessoes = sessoes;
        this.senhas = senhas;
        this.email = email;
        this.auth = auth;
        this.hashDeNenhumaSenha = senhas.encode("nenhuma-senha-" + ALEATORIO.nextLong());
    }

    /** Guarda nome e senha até o código ser confirmado e manda o código. Conta Google sem senha pode ganhar uma. */
    @Transactional
    public void pedirCadastro(String email, String nome, String senha) {
        String endereco = Usuario.normalizarEmail(email);
        if (usuarios.buscarPorEmail(endereco).filter(Usuario::temSenha).isPresent()) {
            throw new ConflitoException("Já existe uma conta com esse e-mail. Entre com a senha ou use \"Esqueci minha senha\".");
        }
        Instant agora = Instant.now();
        if (!liberarParaNovoCodigo(endereco, Finalidade.CADASTRO, agora)) {
            throw new MuitasTentativasException("Já mandamos um código agora há pouco. Espere um minuto para pedir outro.");
        }
        mandarCodigo(endereco, Finalidade.CADASTRO, nome.trim(), hashDaSenha(senha), agora);
    }

    /** O código confere: cria a conta (ou põe a senha na conta Google do mesmo e-mail) e já entra. */
    @Transactional(noRollbackFor = RequisicaoInvalidaException.class)
    public AuthService.Login confirmarCadastro(String email, String codigo) {
        String endereco = Usuario.normalizarEmail(email);
        CodigoDeEmail confirmado = conferir(endereco, Finalidade.CADASTRO, codigo);
        Usuario usuario = usuarios.buscarPorEmail(endereco).map(existente -> {
            if (existente.temSenha()) {
                throw new ConflitoException("Essa conta já tem senha. Entre com ela ou use \"Esqueci minha senha\".");
            }
            existente.definirSenha(confirmado.getSenhaHash());
            existente.completarNome(confirmado.getNome());
            return existente;
        }).orElseGet(() -> Usuario.comSenha(endereco, confirmado.getNome(), confirmado.getSenhaHash()));
        usuarios.save(usuario);
        return auth.abrirSessao(usuario);
    }

    /** Senha errada conta para o bloqueio, por isso a falha também é gravada (sem rollback). */
    @Transactional(noRollbackFor = LoginInvalidoException.class)
    public AuthService.Login entrar(String email, String senha) {
        Instant agora = Instant.now();
        Optional<Usuario> conta = usuarios.buscarPorEmail(Usuario.normalizarEmail(email));
        if (conta.isEmpty() || !conta.get().temSenha()) {
            senhas.matches(senha, hashDeNenhumaSenha);
            if (conta.isPresent()) {
                throw new LoginInvalidoException("Essa conta entra com o Google. Para criar uma senha, use \"Esqueci minha senha\".");
            }
            throw new LoginInvalidoException("E-mail ou senha incorretos.");
        }
        Usuario usuario = conta.get();
        if (usuario.bloqueada(agora)) {
            throw new MuitasTentativasException("Senha errada muitas vezes. Tente de novo em 15 minutos ou use \"Esqueci minha senha\".");
        }
        if (!senhas.matches(senha, usuario.getSenhaHash())) {
            usuario.registrarSenhaErrada(agora);
            throw new LoginInvalidoException("E-mail ou senha incorretos.");
        }
        usuario.registrarLoginComSenha(agora);
        return auth.abrirSessao(usuario);
    }

    /**
     * Manda o código para criar uma senha nova. Responde igual exista a conta ou não, para não
     * revelar quem tem conta; também serve para uma conta Google passar a ter senha.
     */
    @Transactional
    public void pedirNovaSenha(String email) {
        String endereco = Usuario.normalizarEmail(email);
        Instant agora = Instant.now();
        if (usuarios.buscarPorEmail(endereco).isEmpty() || !liberarParaNovoCodigo(endereco, Finalidade.SENHA, agora)) {
            return;
        }
        mandarCodigo(endereco, Finalidade.SENHA, null, null, agora);
    }

    /** Troca a senha, derruba todos os aparelhos que estavam logados e entra neste. */
    @Transactional(noRollbackFor = RequisicaoInvalidaException.class)
    public AuthService.Login redefinirSenha(String email, String codigo, String senha) {
        String endereco = Usuario.normalizarEmail(email);
        String novoHash = hashDaSenha(senha);
        conferir(endereco, Finalidade.SENHA, codigo);
        Usuario usuario = usuarios.buscarPorEmail(endereco)
                .orElseThrow(() -> new RequisicaoInvalidaException("Conta não encontrada."));
        usuario.definirSenha(novoHash);
        sessoes.revogarTodas(usuario.getId(), Instant.now());
        return auth.abrirSessao(usuario);
    }

    /** Código válido some depois de usado. Errado conta tentativa; esgotou, some também. */
    private CodigoDeEmail conferir(String endereco, Finalidade finalidade, String codigo) {
        Instant agora = Instant.now();
        CodigoDeEmail pedido = codigos.findByEmailAndFinalidade(endereco, finalidade)
                .filter(c -> !c.expirado(agora))
                .orElseThrow(() -> new RequisicaoInvalidaException("Código expirado. Peça um novo."));
        if (!pedido.confere(codigo)) {
            if (pedido.semTentativas()) {
                codigos.delete(pedido);
                throw new RequisicaoInvalidaException("Código errado muitas vezes. Peça um novo.");
            }
            throw new RequisicaoInvalidaException("Código incorreto. Confira o e-mail e tente de novo.");
        }
        codigos.delete(pedido);
        return pedido;
    }

    /** Apaga o código anterior. Se ele saiu há menos de um minuto, não libera outro ainda. */
    private boolean liberarParaNovoCodigo(String endereco, Finalidade finalidade, Instant agora) {
        Optional<CodigoDeEmail> anterior = codigos.findByEmailAndFinalidade(endereco, finalidade);
        if (anterior.isPresent()) {
            if (anterior.get().recente(agora)) return false;
            codigos.delete(anterior.get());
            // Apaga já: senão o Hibernate insere o novo antes e esbarra no UNIQUE (email, finalidade).
            codigos.flush();
        }
        return true;
    }

    /** Se o e-mail não sair, a exceção desfaz a transação e o código não fica valendo. */
    private void mandarCodigo(String endereco, Finalidade finalidade, String nome, String senhaHash, Instant agora) {
        String codigo = "%06d".formatted(ALEATORIO.nextInt(1_000_000));
        codigos.save(new CodigoDeEmail(endereco, finalidade, codigo, nome, senhaHash, agora));
        String paraQue = finalidade == Finalidade.CADASTRO ? "criar sua conta no FocaAI" : "criar uma senha nova no FocaAI";
        email.enviar(endereco, "Seu código do FocaAI: " + codigo, """
                Olá!

                Seu código para %s é:

                %s

                Ele vale por 15 minutos. Se não foi você que pediu, é só ignorar este e-mail.
                """.formatted(paraQue, codigo));
    }

    private String hashDaSenha(String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_DA_SENHA) {
            throw new RequisicaoInvalidaException("Senha longa demais. Use até 72 caracteres, sem emojis.");
        }
        return senhas.encode(senha);
    }
}
