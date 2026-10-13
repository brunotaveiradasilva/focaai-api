package com.focaai.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByGoogleSub(String googleSub);

    /** Mesma comparação do índice único {@code lower(email)}. */
    @Query("SELECT u FROM Usuario u WHERE lower(u.email) = lower(:email)")
    Optional<Usuario> buscarPorEmail(String email);
}
