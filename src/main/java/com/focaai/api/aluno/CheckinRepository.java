package com.focaai.api.aluno;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    List<Checkin> findByUsuarioIdOrderByFeitoEm(UUID usuarioId);

    void deleteByUsuarioId(UUID usuarioId);
}
