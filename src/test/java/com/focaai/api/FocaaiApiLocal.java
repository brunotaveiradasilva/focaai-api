package com.focaai.api;

import java.io.IOException;

import com.focaai.api.auth.LoginDeDesenvolvimento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Sobe a API na máquina sem Docker nem PostgreSQL instalado: usa o mesmo PostgreSQL embutido dos
 * testes ({@link BancoDeTeste}), que some quando a API para. Liga também o
 * {@link LoginDeDesenvolvimento} ({@code POST /api/auth/dev}), para usar o app antes de configurar
 * o Google. Rode com {@code ./mvnw spring-boot:test-run}.
 */
public class FocaaiApiLocal {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FocaaiApiApplication.class, BancoDeTeste.class, LoginDeDesenvolvimento.class);
        // Sem isto, a varredura de componentes acharia as configurações de teste (Google e ENEM falsos).
        app.addInitializers(contexto -> contexto.getBeanFactory().registerSingleton("semClassesDeTeste", new SemClassesDeTeste()));
        app.run(args);
    }

    /** Deixa de fora da varredura tudo que é {@code @TestConfiguration} ou {@code @TestComponent}, inclusive classes internas. */
    static class SemClassesDeTeste extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader leitor, MetadataReaderFactory fabrica) throws IOException {
            if (deTeste(leitor)) return true;
            String externa = leitor.getClassMetadata().getEnclosingClassName();
            return externa != null && match(fabrica.getMetadataReader(externa), fabrica);
        }

        private static boolean deTeste(MetadataReader leitor) {
            var anotacoes = leitor.getAnnotationMetadata();
            return anotacoes.hasAnnotation(TestConfiguration.class.getName()) || anotacoes.hasAnnotation(TestComponent.class.getName());
        }

        @Override
        public boolean equals(Object outro) {
            return outro != null && outro.getClass() == getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }
}
