package com.focaai.api.comum;

/** Lançada quando um id pesquisado não existe. Vira 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
