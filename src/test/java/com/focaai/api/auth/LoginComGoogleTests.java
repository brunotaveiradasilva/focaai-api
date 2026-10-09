package com.focaai.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.focaai.api.BancoDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({BancoDeTeste.class, GoogleDeTeste.class})
class LoginComGoogleTests {

    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired SessaoRepository sessoes;

    @BeforeEach
    void limpar() {
        sessoes.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void primeiroLoginCriaAContaEOTokenAbreAApi() {
        JsonNode login = entrar(GoogleDeTeste.idToken("111"));
        assertThat(login.get("expiresIn").asLong()).isEqualTo(3600);
        assertThat(login.get("user").get("name").asString()).isEqualTo("Aluno 111");

        var eu = assertThat(mvc.get().uri("/api/eu").header("Authorization", "Bearer " + login.get("accessToken").asString()))
                .hasStatusOk().bodyJson();
        eu.extractingPath("$.email").isEqualTo("111@gmail.com");
        eu.extractingPath("$.picture").isEqualTo("https://lh3.googleusercontent.com/111");
    }

    @Test
    void loginDeNovoMantemAContaEAtualizaONome() {
        String id = entrar(GoogleDeTeste.idToken("222")).get("user").get("id").asString();
        JsonNode segundo = entrar(GoogleDeTeste.idToken("222", Map.of("name", "Nome Novo")));
        assertThat(segundo.get("user").get("id").asString()).isEqualTo(id);
        assertThat(segundo.get("user").get("name").asString()).isEqualTo("Nome Novo");
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void recusaTokenEmitidoParaOutroApp() {
        assertThat(postarGoogle(GoogleDeTeste.idToken("333", Map.of("aud", List.of("outro-app.apps.googleusercontent.com")))))
                .hasStatus(401);
    }

    @Test
    void recusaTokenQueNaoVeioDoGoogle() {
        assertThat(postarGoogle(GoogleDeTeste.idToken("333", Map.of("iss", "https://falso.example.com")))).hasStatus(401);
    }

    @Test
    void recusaTokenExpirado() {
        Instant ontem = Instant.now().minusSeconds(86_400);
        assertThat(postarGoogle(GoogleDeTeste.idToken("333", Map.of("iat", ontem.minusSeconds(3600), "exp", ontem))))
                .hasStatus(401);
    }

    @Test
    void recusaEmailNaoVerificado() {
        assertThat(postarGoogle(GoogleDeTeste.idToken("333", Map.of("email_verified", false)))).hasStatus(401);
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void recusaLixo() {
        assertThat(postarGoogle("nao-e-um-jwt")).hasStatus(401);
    }

    @Test
    void renovarTrocaOParEInvalidaORefreshAntigo() {
        JsonNode login = entrar(GoogleDeTeste.idToken("444"));
        String refresh = login.get("refreshToken").asString();

        MvcTestResult renovado = postar("/api/auth/renovar", Map.of("refreshToken", refresh));
        assertThat(renovado).hasStatusOk();
        String novoRefresh = ler(renovado).get("refreshToken").asString();
        assertThat(novoRefresh).isNotEqualTo(refresh);

        assertThat(postar("/api/auth/renovar", Map.of("refreshToken", refresh))).hasStatus(401);
        assertThat(postar("/api/auth/renovar", Map.of("refreshToken", novoRefresh))).hasStatusOk();
    }

    @Test
    void sairDerrubaASessao() {
        String refresh = entrar(GoogleDeTeste.idToken("555")).get("refreshToken").asString();
        assertThat(postar("/api/auth/sair", Map.of("refreshToken", refresh))).hasStatus(204);
        assertThat(postar("/api/auth/renovar", Map.of("refreshToken", refresh))).hasStatus(401);
    }

    @Test
    void semTokenSoOConteudoFicaAberto() {
        assertThat(mvc.get().uri("/api/eu")).hasStatus(401);
        assertThat(mvc.get().uri("/api/eu").header("Authorization", "Bearer token-falso")).hasStatus(401);
        assertThat(mvc.get().uri("/api/conteudo")).hasStatusOk();
    }

    private JsonNode entrar(String idToken) {
        MvcTestResult resultado = postarGoogle(idToken);
        assertThat(resultado).hasStatusOk();
        return ler(resultado);
    }

    private MvcTestResult postarGoogle(String idToken) {
        return postar("/api/auth/google", Map.of("idToken", idToken));
    }

    private MvcTestResult postar(String uri, Object corpo) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)).exchange();
    }

    private JsonNode ler(MvcTestResult resultado) {
        try {
            return json.readTree(resultado.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
