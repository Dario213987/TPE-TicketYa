package org.tudai.ticketya.usuarios.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.usuarios.model.TokenResponse;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.exception.UnauthorizedException;
import org.tudai.ticketya.usuarios.domain.Usuario;
import org.tudai.ticketya.usuarios.repository.UsuarioRepository;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioService usuarios;
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    private String hashFicticio;

    @PostConstruct
    void init() {
        hashFicticio = passwordEncoder.encode("contraseña-que-nadie-usa");
    }

    public Usuario registrar(String email, String nombre, String password) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre(nombre);
        usuario.setRoles(new HashSet<>(Set.of(Role.CLIENTE)));
        return usuarios.crear(usuario, password);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        Usuario usuario = repository.findByEmail(UsuarioService.normalizar(email)).orElse(null);
        boolean coincide = passwordEncoder.matches(password, usuario != null ? usuario.getPasswordHash() : hashFicticio);
        if (usuario == null || !coincide) {
            throw new UnauthorizedException("Credenciales inválidas");
        }
        return jwt.emitir(usuario);
    }
}
