package com.focaai.api.aluno;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** O que o aluno registrou depois de estudar, no lugar de um cronômetro de tela travada. */
@Entity
@Table(name = "checkin")
public class Checkin {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "feito_em", nullable = false)
    private Instant feitoEm;

    @Column(name = "tarefa_id")
    private String tarefaId;

    private String materia;

    @Column(name = "topico_id")
    private String topicoId;

    private int minutos;

    private short sensacao;

    private int questoes;

    private int acertos;

    protected Checkin() {
    }

    public Checkin(UUID id, UUID usuarioId, Instant feitoEm, String tarefaId, String materia, String topicoId,
            int minutos, int sensacao, int questoes, int acertos) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.feitoEm = feitoEm;
        this.tarefaId = tarefaId;
        this.materia = materia;
        this.topicoId = topicoId;
        this.minutos = minutos;
        this.sensacao = (short) sensacao;
        this.questoes = questoes;
        this.acertos = acertos;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getFeitoEm() {
        return feitoEm;
    }

    public String getTarefaId() {
        return tarefaId;
    }

    public String getMateria() {
        return materia;
    }

    public String getTopicoId() {
        return topicoId;
    }

    public int getMinutos() {
        return minutos;
    }

    public int getSensacao() {
        return sensacao;
    }

    public int getQuestoes() {
        return questoes;
    }

    public int getAcertos() {
        return acertos;
    }
}
