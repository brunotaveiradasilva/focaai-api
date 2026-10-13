package com.focaai.api;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Chama a API como um aluno logado (ver {@link ContaDeTeste}). */
@TestComponent
public class ClienteDeTeste {

    private final MockMvcTester mvc;
    private final ObjectMapper json;
    private final ContaDeTeste contas;
    private ContaDeTeste.Logado aluno;

    public ClienteDeTeste(MockMvcTester mvc, ObjectMapper json, ContaDeTeste contas) {
        this.mvc = mvc;
        this.json = json;
        this.contas = contas;
    }

    /** Entra com um aluno novo, sem perfil. */
    public ContaDeTeste.Logado novoAluno() {
        aluno = contas.criar();
        return aluno;
    }

    /** Entra com um aluno novo que já fez o onboarding com {@link PerfilDeTeste}. */
    public ContaDeTeste.Logado novoAlunoComPerfil() {
        novoAluno();
        put("/api/eu/perfil", PerfilDeTeste.perfil());
        return aluno;
    }

    public MvcTestResult get(String uri) {
        return enviar(HttpMethod.GET, uri, null);
    }

    public MvcTestResult post(String uri, Object corpo) {
        return enviar(HttpMethod.POST, uri, corpo);
    }

    public MvcTestResult put(String uri, Object corpo) {
        return enviar(HttpMethod.PUT, uri, corpo);
    }

    public MvcTestResult patch(String uri, Object corpo) {
        return enviar(HttpMethod.PATCH, uri, corpo);
    }

    public MvcTestResult delete(String uri) {
        return enviar(HttpMethod.DELETE, uri, null);
    }

    public MvcTestResult enviar(HttpMethod metodo, String uri, Object corpo) {
        var pedido = mvc.method(metodo).uri(uri).header("Authorization", aluno.authorization());
        if (corpo != null) pedido = pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        return pedido.exchange();
    }

    public JsonNode ler(MvcTestResult resultado) {
        try {
            return json.readTree(resultado.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
