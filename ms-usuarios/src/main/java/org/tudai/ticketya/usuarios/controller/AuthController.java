package org.tudai.ticketya.usuarios.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.contract.usuarios.model.LoginRequest;
import org.tudai.ticketya.contract.usuarios.model.RegistroRequest;
import org.tudai.ticketya.contract.usuarios.model.TokenResponse;
import org.tudai.ticketya.contract.usuarios.model.UsuarioResponse;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.usuarios.domain.Usuario;
import org.tudai.ticketya.usuarios.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;
    private final ModelMapper mapper;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest request) {
        try {
            Usuario usuario = service.registrar(request.getEmail(), request.getNombre(), request.getPassword());
            return mapper.map(usuario, UsuarioResponse.class);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.getEmail(), request.getPassword());
    }
}
