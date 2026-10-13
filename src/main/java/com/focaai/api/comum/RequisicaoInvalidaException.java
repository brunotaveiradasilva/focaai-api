package com.focaai.api.comum;

/** Pedido que não faz sentido para a regra de negócio (ex.: simulado sem área). Vira 400. */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
