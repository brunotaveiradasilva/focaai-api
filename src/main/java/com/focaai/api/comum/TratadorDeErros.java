package com.focaai.api.comum;

import java.util.LinkedHashMap;
import java.util.Map;

import com.focaai.api.auth.LoginInvalidoException;
import com.focaai.api.email.EmailNaoEnviadoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/** Converte as exceções da API em respostas JSON simples, em vez da página de erro padrão do Spring. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<Map<String, String>> requisicaoInvalida(RequisicaoInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<Map<String, String>> conflito(ConflitoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(LoginInvalidoException.class)
    public ResponseEntity<Map<String, String>> loginInvalido(LoginInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<Map<String, String>> muitasTentativas(MuitasTentativasException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(EmailNaoEnviadoException.class)
    public ResponseEntity<Map<String, String>> emailNaoEnviado(EmailNaoEnviadoException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", ex.getMessage()));
    }

    /** api.enem.dev fora do ar ou recusando: o problema não é de quem chamou, é do serviço de trás. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> servicoExternoFalhou(RestClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("erro", "Não foi possível baixar as questões agora. Tente de novo em instantes."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacaoFalhou(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(erro ->
                campos.put(erro.getField(), erro.getDefaultMessage()));

        Map<String, Object> corpo = new LinkedHashMap<>();
        // Um campo só: a mensagem dele já explica o problema para quem usa o app.
        corpo.put("erro", campos.size() == 1 ? campos.values().iterator().next() : "Dados inválidos");
        corpo.put("campos", campos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }
}
