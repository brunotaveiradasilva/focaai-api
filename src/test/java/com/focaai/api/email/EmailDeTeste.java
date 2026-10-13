package com.focaai.api.email;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Guarda os e-mails em vez de mandar, para o teste ler o código que chegaria na caixa de entrada. */
@TestConfiguration(proxyBeanMethods = false)
public class EmailDeTeste {

    @Bean
    @Primary
    CaixaDeEntrada caixaDeEntrada() {
        return new CaixaDeEntrada();
    }

    public static class CaixaDeEntrada implements EnviadorDeEmail {

        private static final Pattern CODIGO = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");

        public record Mensagem(String para, String assunto, String texto) {
        }

        private final List<Mensagem> mensagens = new ArrayList<>();

        @Override
        public synchronized void enviar(String para, String assunto, String texto) {
            mensagens.add(new Mensagem(para, assunto, texto));
        }

        public synchronized List<Mensagem> mensagens() {
            return List.copyOf(mensagens);
        }

        /** Código do último e-mail mandado para {@code para}. */
        public synchronized String ultimoCodigo(String para) {
            for (int i = mensagens.size() - 1; i >= 0; i--) {
                if (mensagens.get(i).para().equals(para)) {
                    Matcher m = CODIGO.matcher(mensagens.get(i).texto());
                    if (m.find()) return m.group(1);
                }
            }
            throw new AssertionError("Nenhum código mandado para " + para);
        }

        public synchronized void esvaziar() {
            mensagens.clear();
        }
    }
}
