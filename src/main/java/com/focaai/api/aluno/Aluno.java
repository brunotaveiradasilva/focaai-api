package com.focaai.api.aluno;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Estado de estudo de um aluno. As colunas jsonb chegam aqui como texto e o {@link AlunoService}
 * converte para os tipos de verdade; assim o Hibernate não precisa saber de JSON.
 */
@Entity
@Table(name = "aluno")
public class Aluno {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String perfil;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String progresso;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String foco;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String reforco;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Aluno() {
    }

    public Aluno(UUID usuarioId) {
        this.usuarioId = usuarioId;
        this.progresso = "{}";
        this.foco = "{}";
        this.reforco = "{}";
        this.criadoEm = Instant.now();
        this.atualizadoEm = criadoEm;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
        tocar();
    }

    public String getProgresso() {
        return progresso;
    }

    public void setProgresso(String progresso) {
        this.progresso = progresso;
        tocar();
    }

    public String getFoco() {
        return foco;
    }

    public void setFoco(String foco) {
        this.foco = foco;
        tocar();
    }

    public String getReforco() {
        return reforco;
    }

    public void setReforco(String reforco) {
        this.reforco = reforco;
        tocar();
    }

    private void tocar() {
        this.atualizadoEm = Instant.now();
    }
}
