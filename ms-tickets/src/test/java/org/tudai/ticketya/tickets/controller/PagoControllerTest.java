package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.domain.EstadoPago;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.dto.TicketEmitidoDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.service.PagoService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagoControllerTest {

    private PagoService pagoService;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        pagoService = mock(PagoService.class);
        userContext = mock(UserContext.class);
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.CLIENTE);
        mvc = MockMvcBuilders.standaloneSetup(new PagoController(pagoService, userContext))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    @Test
    void pagar_conRolDistintoDeCliente_respondeForbiddenSinPagar() throws Exception {
        for (Role rol : List.of(Role.ORGANIZADOR, Role.ADMINISTRADOR, Role.PERSONAL_ACCESO)) {
            when(userContext.role()).thenReturn(rol);

            mvc.perform(post("/api/tickets/reservas/99/pago")
                            .header("Idempotency-Key", "clave-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"medioPago\":\"TARJETA\"}"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(pagoService);
    }

    @Test
    void pagar_respondeCreatedConLasEntradasEmitidas() throws Exception {
        when(pagoService.pagar(eq(99L), eq(42L), eq("clave-1"), any(PagoRequestDTO.class)))
                .thenReturn(CompraResponseDTO.builder()
                        .reservaId(99L)
                        .clienteId(42L)
                        .medioPago("TARJETA")
                        .estadoPago(EstadoPago.APROBADO)
                        .total(1500.0)
                        .tickets(List.of(TicketEmitidoDTO.builder()
                                .id(1L)
                                .codigoQr("qr-1")
                                .estado(EstadoTicket.VENDIDO)
                                .precio(1500.0)
                                .build()))
                        .build());

        mvc.perform(post("/api/tickets/reservas/99/pago")
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medioPago\":\"TARJETA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.medioPago").value("TARJETA"))
                .andExpect(jsonPath("$.tickets[0].codigoQr").value("qr-1"))
                .andExpect(jsonPath("$.tickets[0].estado").value("VENDIDO"));

        verify(pagoService).pagar(eq(99L), eq(42L), eq("clave-1"), any(PagoRequestDTO.class));
    }

    @Test
    void pagar_pagoPendiente_respondeAcceptedParaReintentar() throws Exception {
        when(pagoService.pagar(eq(99L), eq(42L), eq("clave-1"), any(PagoRequestDTO.class)))
                .thenReturn(CompraResponseDTO.builder()
                        .reservaId(99L)
                        .estadoPago(EstadoPago.PENDIENTE_PAGO)
                        .tickets(List.of())
                        .build());

        mvc.perform(post("/api/tickets/reservas/99/pago")
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medioPago\":\"TARJETA\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estadoPago").value("PENDIENTE_PAGO"));
    }

    @Test
    void pagar_sinIdempotencyKey_respondeBadRequest() throws Exception {
        mvc.perform(post("/api/tickets/reservas/99/pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medioPago\":\"TARJETA\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pagoService);
    }

    @Test
    void pagar_sinMedioDePago_respondeBadRequest() throws Exception {
        mvc.perform(post("/api/tickets/reservas/99/pago")
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pagoService);
    }

    @Test
    void pagar_pagoRechazado_respondeConflictConElMotivo() throws Exception {
        when(pagoService.pagar(eq(99L), eq(42L), eq("clave-1"), any(PagoRequestDTO.class)))
                .thenThrow(new PagoRechazadoException("fondos insuficientes"));

        mvc.perform(post("/api/tickets/reservas/99/pago")
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medioPago\":\"TARJETA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pago fue rechazado: fondos insuficientes"));
    }
}
