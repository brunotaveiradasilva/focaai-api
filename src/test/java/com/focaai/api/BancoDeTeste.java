package com.focaai.api;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * PostgreSQL de verdade para os testes, rodando dentro do processo (sem Docker). Um único banco
 * é compartilhado por todos os testes da execução; cada teste limpa o que criou.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BancoDeTeste {

    private static EmbeddedPostgres postgres;

    private static synchronized EmbeddedPostgres postgres() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return postgres;
    }

    @Bean
    DataSource dataSource() {
        return postgres().getPostgresDatabase();
    }
}
