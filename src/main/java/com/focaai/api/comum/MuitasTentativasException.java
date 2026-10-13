package com.focaai.api.comum;

/** Pedido repetido rápido demais (código reenviado, senha errada várias vezes). Vira 429. */
public class MuitasTentativasException extends RuntimeException {

    public MuitasTentativasException(String mensagem) {
        super(mensagem);
    }
}
