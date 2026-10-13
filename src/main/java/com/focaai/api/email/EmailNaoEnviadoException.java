package com.focaai.api.email;

/** O e-mail não saiu (serviço fora do ar ou ainda não configurado). Vira 503. */
public class EmailNaoEnviadoException extends RuntimeException {

    public EmailNaoEnviadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
