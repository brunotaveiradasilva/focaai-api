package com.focaai.api.email;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Manda pela API HTTP do Resend (resend.com). HTTP e não SMTP porque o Railway bloqueia SMTP de
 * saída. Com o remetente de teste {@code onboarding@resend.dev}, o Resend só entrega para o e-mail
 * da própria conta do Resend; para qualquer pessoa, o remetente precisa ser de um domínio verificado lá.
 */
@Component
public class EmailPeloResend implements EnviadorDeEmail {

    private static final Logger log = LoggerFactory.getLogger(EmailPeloResend.class);

    private final RestClient http;
    private final String chave;
    private final String remetente;

    public EmailPeloResend(
            @Value("${app.email.resend-url}") String url,
            @Value("${app.email.resend-api-key}") String chave,
            @Value("${app.email.remetente}") String remetente) {
        this.http = RestClient.builder().baseUrl(url).build();
        this.chave = chave;
        this.remetente = remetente;
        if (chave.isBlank()) {
            log.warn("RESEND_API_KEY vazio: cadastro com e-mail e \"esqueci minha senha\" ficam fora até ele ser configurado.");
        }
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        if (chave.isBlank()) {
            throw new EmailNaoEnviadoException("Entrar com e-mail ainda não está disponível. Use o Google por enquanto.", null);
        }
        try {
            http.post().uri("/emails")
                    .header("Authorization", "Bearer " + chave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", remetente, "to", List.of(para), "subject", assunto, "text", texto))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // A resposta do Resend explica o motivo (ex.: domínio não verificado); o endereço não vai para o log.
            log.error("Resend recusou o e-mail: {}", e.getMessage());
            throw new EmailNaoEnviadoException("Não deu para mandar o e-mail agora. Tente de novo em instantes.", e);
        }
    }
}
