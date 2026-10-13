package com.focaai.api.simulado;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/** Um simulado do aluno. Áreas, questões e respostas ficam em jsonb, convertidas pelo {@link SimuladoService}. */
@Entity
@Table(name = "simulado")
public class Simulado {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "finalizado_em")
    private Instant finalizadoEm;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String areas;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String questoes;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String respostas;

    private int segundos;

    protected Simulado() {
    }

    public Simulado(UUID id, UUID usuarioId, Instant criadoEm, String areas, String questoes) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
        this.areas = areas;
        this.questoes = questoes;
        this.respostas = "{}";
    }

    public boolean emAndamento() {
        return finalizadoEm == null;
    }

    public void finalizar(Instant quando) {
        this.finalizadoEm = quando;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getFinalizadoEm() {
        return finalizadoEm;
    }

    public String getAreas() {
        return areas;
    }

    public String getQuestoes() {
        return questoes;
    }

    public String getRespostas() {
        return respostas;
    }

    public void setRespostas(String respostas) {
        this.respostas = respostas;
    }

    public int getSegundos() {
        return segundos;
    }

    public void setSegundos(int segundos) {
        this.segundos = segundos;
    }
}
