package com.focaai.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.focaai.api.BancoDeTeste;
import com.focaai.api.email.EmailDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, GoogleDeTeste.class, EmailDeTeste.class})
class LoginComEmailTests {

    static final String SENHA = "senha-boa-123";

    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired SessaoRepository sessoes;
    @Autowired JdbcClient jdbc;
    @Autowired EmailDeTeste.CaixaDeEntrada caixa;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM codigo_de_email").update();
        sessoes.deleteAll();
        usuarios.deleteAll();
        caixa.esvaziar();
    }

    @Test
    void cadastroSoCriaAContaDepoisDoCodigoEJaEntra() {
        assertThat(cadastrar("ana@exemplo.com", "Ana", SENHA)).hasStatus(204);
        assertThat(usuarios.count()).isZero();
        assertThat(caixa.mensagens()).singleElement().satisfies(m -> {
            assertThat(m.para()).isEqualTo("ana@exemplo.com");
            assertThat(m.assunto()).contains(caixa.ultimoCodigo("ana@exemplo.com"));
        });

        JsonNode login = ler(ok(confirmar("ana@exemplo.com", caixa.ultimoCodigo("ana@exemplo.com"))));
        assertThat(login.get("user").get("name").asString()).isEqualTo("Ana");
        assertThat(mvc.get().uri("/api/eu").header("Authorization", "Bearer " + login.get("accessToken").asString()))
                .hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo("ana@exemplo.com");

        JsonNode deNovo = ler(ok(entrar("ana@exemplo.com", SENHA)));
        assertThat(deNovo.get("user").get("id").asString()).isEqualTo(login.get("user").get("id").asString());
    }

    @Test
    void emailNaoDiferenciaMaiusculas() {
        cadastrar("Bia@Exemplo.com", "Bia", SENHA);
        String codigo = caixa.ultimoCodigo("bia@exemplo.com");
        assertThat(confirmar("BIA@exemplo.com", codigo)).hasStatusOk();
        assertThat(entrar("bia@EXEMPLO.com", SENHA)).hasStatusOk();
    }

    @Test
    void senhaErradaRecusaEDezSeguidasBloqueiam() {
        criarConta("cai@exemplo.com");
        MvcTestResult errada = entrar("cai@exemplo.com", "outra-senha");
        assertThat(errada).hasStatus(401).bodyJson().extractingPath("$.erro").isEqualTo("E-mail ou senha incorretos.");
        for (int i = 1; i < Usuario.FALHAS_ATE_BLOQUEAR; i++) entrar("cai@exemplo.com", "outra-senha");

        assertThat(entrar("cai@exemplo.com", SENHA)).hasStatus(429);

        jdbc.sql("UPDATE usuario SET bloqueada_ate = now() - interval '1 minute'").update();
        assertThat(entrar("cai@exemplo.com", SENHA)).hasStatusOk();
    }

    @Test
    void emailSemContaRespondeIgualASenhaErrada() {
        assertThat(entrar("ninguem@exemplo.com", SENHA)).hasStatus(401)
                .bodyJson().extractingPath("$.erro").isEqualTo("E-mail ou senha incorretos.");
    }

    @Test
    void codigoErradoGastaTentativasEAQuintaInvalidaOCodigo() {
        cadastrar("dan@exemplo.com", "Dan", SENHA);
        String certo = caixa.ultimoCodigo("dan@exemplo.com");
        String errado = certo.equals("000000") ? "111111" : "000000";
        for (int i = 1; i < CodigoDeEmail.TENTATIVAS; i++) {
            assertThat(confirmar("dan@exemplo.com", errado)).hasStatus(400)
                    .bodyJson().extractingPath("$.erro").asString().startsWith("Código incorreto");
        }
        assertThat(confirmar("dan@exemplo.com", errado)).hasStatus(400)
                .bodyJson().extractingPath("$.erro").asString().contains("muitas vezes");

        assertThat(confirmar("dan@exemplo.com", certo)).hasStatus(400);
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void codigoVencidoNaoServe() {
        cadastrar("eva@exemplo.com", "Eva", SENHA);
        jdbc.sql("UPDATE codigo_de_email SET expira_em = now() - interval '1 minute'").update();
        assertThat(confirmar("eva@exemplo.com", caixa.ultimoCodigo("eva@exemplo.com"))).hasStatus(400)
                .bodyJson().extractingPath("$.erro").asString().contains("expirado");
    }

    @Test
    void novoCodigoSoDepoisDeUmMinutoESubstituiOAnterior() {
        cadastrar("fred@exemplo.com", "Fred", SENHA);
        String primeiro = caixa.ultimoCodigo("fred@exemplo.com");
        assertThat(cadastrar("fred@exemplo.com", "Fred", SENHA)).hasStatus(429);
        assertThat(caixa.mensagens()).hasSize(1);

        jdbc.sql("UPDATE codigo_de_email SET criado_em = now() - interval '2 minutes'").update();
        assertThat(cadastrar("fred@exemplo.com", "Fred", SENHA)).hasStatus(204);
        String segundo = caixa.ultimoCodigo("fred@exemplo.com");
        if (!segundo.equals(primeiro)) assertThat(confirmar("fred@exemplo.com", primeiro)).hasStatus(400);
        assertThat(confirmar("fred@exemplo.com", segundo)).hasStatusOk();
    }

    @Test
    void emailQueJaTemSenhaNaoCadastraDeNovo() {
        criarConta("gil@exemplo.com");
        assertThat(cadastrar("gil@exemplo.com", "Outro", SENHA)).hasStatus(409);
        assertThat(caixa.mensagens()).isEmpty();
    }

    @Test
    void esqueciASenhaTrocaASenhaEDerrubaOsOutrosAparelhos() {
        String refreshAntigo = criarConta("hal@exemplo.com").get("refreshToken").asString();

        assertThat(postar("/api/auth/senha/esqueci", Map.of("email", "ninguem@exemplo.com"))).hasStatus(204);
        assertThat(caixa.mensagens()).isEmpty();

        assertThat(postar("/api/auth/senha/esqueci", Map.of("email", "hal@exemplo.com"))).hasStatus(204);
        String codigo = caixa.ultimoCodigo("hal@exemplo.com");
        assertThat(postar("/api/auth/senha/redefinir", Map.of("email", "hal@exemplo.com", "code", codigo, "password", "senha-nova-456")))
                .hasStatusOk();

        assertThat(postar("/api/auth/renovar", Map.of("refreshToken", refreshAntigo))).hasStatus(401);
        assertThat(entrar("hal@exemplo.com", SENHA)).hasStatus(401);
        assertThat(entrar("hal@exemplo.com", "senha-nova-456")).hasStatusOk();
    }

    @Test
    void googleNoMesmoEmailEntraNaMesmaConta() {
        String id = criarConta("777@gmail.com").get("user").get("id").asString();
        JsonNode google = ler(ok(postar("/api/auth/google", Map.of("idToken", GoogleDeTeste.idToken("777")))));
        assertThat(google.get("user").get("id").asString()).isEqualTo(id);
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(entrar("777@gmail.com", SENHA)).hasStatusOk();
    }

    @Test
    void contaDoGoogleGanhaSenhaPeloCadastro() {
        JsonNode google = ler(ok(postar("/api/auth/google", Map.of("idToken", GoogleDeTeste.idToken("888")))));
        assertThat(entrar("888@gmail.com", SENHA)).hasStatus(401)
                .bodyJson().extractingPath("$.erro").asString().contains("Google");

        cadastrar("888@gmail.com", "Outro nome", SENHA);
        JsonNode comSenha = ler(ok(confirmar("888@gmail.com", caixa.ultimoCodigo("888@gmail.com"))));
        assertThat(comSenha.get("user").get("id").asString()).isEqualTo(google.get("user").get("id").asString());
        assertThat(comSenha.get("user").get("name").asString()).isEqualTo("Aluno 888");
        assertThat(entrar("888@gmail.com", SENHA)).hasStatusOk();
    }

    @Test
    void senhaCurtaEhRecusadaComMensagemClara() {
        assertThat(cadastrar("ivo@exemplo.com", "Ivo", "curta")).hasStatus(400)
                .bodyJson().extractingPath("$.erro").isEqualTo("A senha precisa ter de 8 a 72 caracteres.");
        assertThat(caixa.mensagens()).isEmpty();
    }

    private JsonNode criarConta(String email) {
        assertThat(cadastrar(email, "Aluno", SENHA)).hasStatus(204);
        JsonNode login = ler(ok(confirmar(email, caixa.ultimoCodigo(email))));
        caixa.esvaziar();
        return login;
    }

    private MvcTestResult cadastrar(String email, String nome, String senha) {
        return postar("/api/auth/cadastro", Map.of("email", email, "name", nome, "password", senha));
    }

    private MvcTestResult confirmar(String email, String codigo) {
        return postar("/api/auth/cadastro/confirmar", Map.of("email", email, "code", codigo));
    }

    private MvcTestResult entrar(String email, String senha) {
        return postar("/api/auth/entrar", Map.of("email", email, "password", senha));
    }

    private MvcTestResult postar(String uri, Object corpo) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)).exchange();
    }

    private MvcTestResult ok(MvcTestResult resultado) {
        assertThat(resultado).hasStatusOk();
        return resultado;
    }

    private JsonNode ler(MvcTestResult resultado) {
        try {
            return json.readTree(resultado.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
