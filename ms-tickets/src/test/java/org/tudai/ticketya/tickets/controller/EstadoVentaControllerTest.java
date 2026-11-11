package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.EstadoVenta;
import org.tudai.ticketya.tickets.service.DisponibilidadService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstadoVentaControllerTest {

    private DisponibilidadService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DisponibilidadService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EstadoVentaController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void cancelar_actualizaElEstadoYCierraLaVenta() throws Exception {
        mvc.perform(put("/api/tickets/internal/eventos/5/cancelar"))
                .andExpect(status().isNoContent());

        verify(service).actualizarEstadoVenta(5L, EstadoVenta.CANCELADA);
    }

    @Test
    void suspender_actualizaElEstadoYSuspendeLaVenta() throws Exception {
        mvc.perform(put("/api/tickets/internal/eventos/5/suspender"))
                .andExpect(status().isNoContent());

        verify(service).actualizarEstadoVenta(5L, EstadoVenta.SUSPENDIDA);
    }

    @Test
    void reactivar_reabreLaVenta() throws Exception {
        mvc.perform(put("/api/tickets/internal/eventos/5/reactivar"))
                .andExpect(status().isNoContent());

        verify(service).actualizarEstadoVenta(5L, EstadoVenta.ABIERTA);
    }

    @Test
    void finalizar_cierraLaVenta() throws Exception {
        mvc.perform(put("/api/tickets/internal/eventos/5/finalizar"))
                .andExpect(status().isNoContent());

        verify(service).actualizarEstadoVenta(5L, EstadoVenta.FINALIZADA);
    }

    @Test
    void cancelar_eventoSinDisponibilidad_responde404ConFormatoEstandar() throws Exception {
        doThrow(new NotFoundException("No hay disponibilidad para el evento 99"))
                .when(service).actualizarEstadoVenta(99L, EstadoVenta.CANCELADA);

        mvc.perform(put("/api/tickets/internal/eventos/99/cancelar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value("No hay disponibilidad para el evento 99"));
    }
}
