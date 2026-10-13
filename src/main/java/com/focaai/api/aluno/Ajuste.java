package com.focaai.api.aluno;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma mudança que o plano fez a partir de um check-in, explicada para o aluno. */
@Entity
@Table(name = "ajuste")
public class Ajuste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "feito_em", nullable = false)
    private Instant feitoEm;

    private String materia;

    @Column(nullable = false)
    private String texto;

    protected Ajuste() {
    }

    public Ajuste(UUID usuarioId, Instant feitoEm, String materia, String texto) {
        this.usuarioId = usuarioId;
        this.feitoEm = feitoEm;
        this.materia = materia;
        this.texto = texto;
    }

    public Instant getFeitoEm() {
        return feitoEm;
    }

    public String getMateria() {
        return materia;
    }

    public String getTexto() {
        return texto;
    }
}
