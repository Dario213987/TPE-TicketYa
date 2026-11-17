package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.dto.reporte.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteOcupacionDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteRecaudacionDTO;
import org.tudai.ticketya.tickets.service.ReporteService;

@RestController
@RequestMapping("/api/tickets/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService service;
    private final UserContext userContext;

    @GetMapping("/eventos/{id}/entradas")
    @Authorize("hasAnyRole('ADMINISTRADOR','ORGANIZADOR')")
    public ResponseEntity<ReporteEntradasVendidasDTO> obtenerReporteEntradasVendidas(
            @PathVariable long id) {
        Long solicitanteId = userContext.userId();
        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;

        return ResponseEntity.ok(service.obtenerReporteEntradasVendidas(id, solicitanteId, esAdmin));
    }

    @GetMapping("/eventos/{id}/ocupacion")
    @Authorize("hasAnyRole('ADMINISTRADOR','ORGANIZADOR')")
    public ResponseEntity<ReporteOcupacionDTO> obtenerReporteOcupacion(
            @PathVariable long id) {
        Long solicitanteId = userContext.userId();
        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;

        return ResponseEntity.ok(service.obtenerReporteOcupacion(id, solicitanteId, esAdmin));
    }

    @GetMapping("/eventos/{id}/recaudacion")
    @Authorize("hasAnyRole('ADMINISTRADOR','ORGANIZADOR')")
    public ResponseEntity<ReporteRecaudacionDTO> obtenerReporteRecaudacion(
            @PathVariable long id) {
        Long solicitanteId = userContext.userId();
        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;

        return ResponseEntity.ok(service.obtenerReporteRecaudacion(id, solicitanteId, esAdmin));
    }
}
