package org.tudai.ticketya.preferencias.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.contract.preferencias.model.RecomendacionResponseDTO;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.preferencias.service.RecomendacionEndpointService;

import java.util.List;

/**
 * Eventos sugeridos para el cliente autenticado.
 */
@RestController
@RequestMapping("/api/recomendaciones")
@RequiredArgsConstructor
public class RecomendacionController {

    private final RecomendacionEndpointService recomendacionEndpointService;
    private final UserContext userContext;

    @GetMapping
    @Authorize("hasRole('CLIENTE')")
    public List<RecomendacionResponseDTO> recomendar() {
        return recomendacionEndpointService.recomendar(userContext.userId());
    }
}
