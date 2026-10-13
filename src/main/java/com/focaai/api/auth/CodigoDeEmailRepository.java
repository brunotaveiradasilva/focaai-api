package com.focaai.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoDeEmailRepository extends JpaRepository<CodigoDeEmail, UUID> {

    Optional<CodigoDeEmail> findByEmailAndFinalidade(String email, CodigoDeEmail.Finalidade finalidade);
}
