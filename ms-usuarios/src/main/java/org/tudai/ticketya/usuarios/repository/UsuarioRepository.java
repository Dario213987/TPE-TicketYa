package org.tudai.ticketya.usuarios.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.usuarios.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmail(String email);
}