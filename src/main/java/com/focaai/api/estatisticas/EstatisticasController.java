package com.focaai.api.estatisticas;

import java.time.LocalDate;
import java.time.ZoneId;

import com.focaai.api.auth.UsuarioAtual;
import com.focaai.api.comum.RequisicaoInvalidaException;
import com.focaai.api.plano.Datas;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EstatisticasController {

    private final EstatisticasService estatisticas;

    public EstatisticasController(EstatisticasService estatisticas) {
        this.estatisticas = estatisticas;
    }

    /** Evolução do aluno nos últimos {@code dias} (1 a 90; o app usa 7 e 30). */
    @GetMapping("/api/eu/estatisticas")
    public EstatisticasService.Resumo resumo(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Timezone", required = false) String fuso,
            @RequestParam(defaultValue = "7") int dias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        if (dias < 1 || dias > 90) throw new RequisicaoInvalidaException("dias precisa estar entre 1 e 90.");
        ZoneId zona = Datas.fuso(fuso);
        return estatisticas.resumo(UsuarioAtual.id(jwt), data != null ? data : LocalDate.now(zona), zona, dias);
    }
}
