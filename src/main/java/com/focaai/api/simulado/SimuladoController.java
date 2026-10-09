package com.focaai.api.simulado;

import java.util.List;
import java.util.UUID;

import com.focaai.api.auth.UsuarioAtual;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Banco de Questões: simulados com questões reais do ENEM. */
@RestController
@RequestMapping("/api/eu/simulados")
public class SimuladoController {

    private final SimuladoService simulados;

    public SimuladoController(SimuladoService simulados) {
        this.simulados = simulados;
    }

    /** Monta um simulado e deixa em andamento. Pode levar alguns segundos se precisar baixar uma prova. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SimuladoService.SimuladoDoAluno criar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NovoSimulado pedido) {
        return simulados.criar(UsuarioAtual.id(jwt), pedido);
    }

    @GetMapping
    public List<SimuladoService.ResumoDeSimulado> finalizados(@AuthenticationPrincipal Jwt jwt) {
        return simulados.finalizados(UsuarioAtual.id(jwt));
    }

    /** O simulado em andamento, ou 204 se não houver. */
    @GetMapping("/em-andamento")
    public ResponseEntity<SimuladoService.SimuladoDoAluno> emAndamento(@AuthenticationPrincipal Jwt jwt) {
        return simulados.emAndamento(UsuarioAtual.id(jwt)).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/em-andamento/respostas/{questao}")
    public SimuladoService.SimuladoDoAluno responder(@AuthenticationPrincipal Jwt jwt, @PathVariable String questao,
            @RequestBody Resposta corpo) {
        return simulados.responder(UsuarioAtual.id(jwt), questao, corpo.letter());
    }

    @PutMapping("/em-andamento/tempo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarTempo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Tempo corpo) {
        simulados.registrarTempo(UsuarioAtual.id(jwt), corpo.seconds());
    }

    @PostMapping("/em-andamento/finalizar")
    public SimuladoService.SimuladoDoAluno finalizar(@AuthenticationPrincipal Jwt jwt) {
        return simulados.finalizar(UsuarioAtual.id(jwt));
    }

    @DeleteMapping("/em-andamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@AuthenticationPrincipal Jwt jwt) {
        simulados.descartar(UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}")
    public SimuladoService.SimuladoDoAluno buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return simulados.buscar(UsuarioAtual.id(jwt), id);
    }

    public record Resposta(String letter) {
    }

    public record Tempo(@Min(0) int seconds) {
    }
}
