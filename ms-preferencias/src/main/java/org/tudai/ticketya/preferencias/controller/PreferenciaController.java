package org.tudai.ticketya.preferencias.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.contract.preferencias.model.HistorialCompraDTO;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaRequestDTO;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaResponseDTO;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.preferencias.service.HistorialService;
import org.tudai.ticketya.preferencias.service.PreferenciaService;

import java.util.List;

/**
 * Preferencias del cliente autenticado.
 * <p>
 * El cliente sale siempre de los headers de la request ({@code X-User-Id}),
 * nunca del path, para que nadie pueda leer o pisar las preferencias de otro.
 */
@RestController
@RequestMapping("/api/preferencias")
@RequiredArgsConstructor
public class PreferenciaController {

    private final PreferenciaService preferenciaService;
    private final HistorialService historialService;
    private final UserContext userContext;

    @GetMapping("/mias")
    @Authorize("hasRole('CLIENTE')")
    public PreferenciaResponseDTO obtenerMisPreferencias() {
        return preferenciaService.obtener(userContext.userId());
    }

    @PutMapping("/mias")
    @Authorize("hasRole('CLIENTE')")
    public PreferenciaResponseDTO guardarMisPreferencias(
            @Valid @RequestBody PreferenciaRequestDTO request) {
        return preferenciaService.guardar(userContext.userId(), request);
    }

    @GetMapping("/historial")
    @Authorize("hasRole('CLIENTE')")
    public List<HistorialCompraDTO> obtenerHistorial() {
        return historialService.obtenerHistorial(userContext.userId());
    }
}
