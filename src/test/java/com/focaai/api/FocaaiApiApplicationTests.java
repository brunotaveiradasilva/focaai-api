package com.focaai.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BancoDeTeste.class)
class FocaaiApiApplicationTests {

    @Autowired MockMvcTester mvc;

    @Test
    void sobeEMostraQueEstaDePe() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson()
                .extractingPath("$.status").isEqualTo("UP");
    }
}
