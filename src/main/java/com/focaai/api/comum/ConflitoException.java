package com.focaai.api.comum;

/** O pedido não combina com o estado atual (ex.: importar sobre uma conta que já tem dados). Vira 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
