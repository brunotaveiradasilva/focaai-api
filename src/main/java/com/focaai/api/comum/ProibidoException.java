package com.focaai.api.comum;

/** O aluno existe e está logado, mas não pode ver ou mexer nisso (ex.: posts de quem não é amigo). Vira 403. */
public class ProibidoException extends RuntimeException {

    public ProibidoException(String mensagem) {
        super(mensagem);
    }
}
