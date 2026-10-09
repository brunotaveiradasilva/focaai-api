package com.focaai.api.aluno;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    /** Trava a linha até o fim da transação: dois aparelhos gravando ao mesmo tempo não se atropelam. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aluno a where a.usuarioId = :usuarioId")
    Optional<Aluno> buscarParaAlterar(UUID usuarioId);
}
