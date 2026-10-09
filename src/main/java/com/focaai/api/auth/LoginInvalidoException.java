package com.focaai.api.auth;

/** Token do Google ou refresh token recusado. Vira 401. */
public class LoginInvalidoException extends RuntimeException {

    public LoginInvalidoException(String mensagem) {
        super(mensagem);
    }
}
