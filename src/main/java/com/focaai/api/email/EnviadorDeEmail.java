package com.focaai.api.email;

/** Manda um e-mail. Falhou ou não está configurado: {@link EmailNaoEnviadoException}. */
public interface EnviadorDeEmail {

    void enviar(String para, String assunto, String texto);
}
