package org.tudai.ticketya.usuarios.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.usuarios.domain.Usuario;
import org.tudai.ticketya.usuarios.repository.UsuarioRepository;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public Usuario crear(Usuario usuario, String password) {
        usuario.setEmail(normalizar(usuario.getEmail()));
        usuario.setPasswordHash(passwordEncoder.encode(password));
        return repository.saveAndFlush(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("El usuario " + id + " no existe"));
    }

    @Transactional(readOnly = true)
    public Page<Usuario> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Usuario cambiarRoles(Long id, Set<Role> roles) {
        Usuario usuario = obtener(id);
        usuario.setRoles(new HashSet<>(roles));
        return usuario;
    }

    public void crearAdministradorSiNoExiste(String email, String password) {
        if (repository.existsByEmail(normalizar(email))) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setEmail(email);
        admin.setNombre("Administrador");
        admin.setRoles(new HashSet<>(Set.of(Role.ADMINISTRADOR)));
        crear(admin, password);
    }

    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}