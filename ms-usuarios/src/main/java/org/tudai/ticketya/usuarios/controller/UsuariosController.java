package org.tudai.ticketya.usuarios.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.contract.usuarios.model.CambiarRolesRequest;
import org.tudai.ticketya.contract.usuarios.model.CrearUsuarioRequest;
import org.tudai.ticketya.contract.usuarios.model.UsuarioResponse;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.usuarios.domain.Usuario;
import org.tudai.ticketya.usuarios.service.UsuarioService;

import java.util.Set;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Authorize("hasRole('ADMINISTRADOR')")
public class UsuariosController {

    private static final Set<String> ORDENABLES = Set.of("id", "email", "nombre", "creadoEl");

    private final UsuarioService service;
    private final ModelMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crearUsuario(@Valid @RequestBody CrearUsuarioRequest request) {
        Usuario usuario = mapper.map(request, Usuario.class);
        try {
            return aResponse(service.crear(usuario, request.getPassword()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtenerUsuario(@PathVariable Long id) {
        return aResponse(service.obtener(id));
    }

    @GetMapping
    public PagedModel<UsuarioResponse> listarUsuarios(@PageableDefault(sort = {"creadoEl", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {

        pageable.getSort().forEach(orden -> {
            if (!ORDENABLES.contains(orden.getProperty())) {
                throw new BadRequestException(
                        "No se puede ordenar por '" + orden.getProperty() + "'. Campos válidos: " + ORDENABLES);
            }
        });

        Page<Usuario> page = service.listar(pageable);
        return new PagedModel<>(page.map(this::aResponse));
    }

    @PutMapping("/{id}/roles")
    public UsuarioResponse cambiarRoles(@PathVariable Long id, @Valid @RequestBody CambiarRolesRequest request) {
        return aResponse(service.cambiarRoles(id, request.getRoles()));
    }

    private UsuarioResponse aResponse(Usuario usuario) {
        return mapper.map(usuario, UsuarioResponse.class);
    }
}