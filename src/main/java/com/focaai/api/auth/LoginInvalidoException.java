package com.focaai.api.auth;

/** Token do Google, senha ou refresh token recusado. Vira 401. */
public class LoginInvalidoException extends RuntimeException {

    public LoginInvalidoException(String mensagem) {
        super(mensagem);
    }
}
