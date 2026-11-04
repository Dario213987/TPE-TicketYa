package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.dto.ReservaRequestDTO;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.service.ReservaService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservaControllerTest {

    private ReservaService reservaService;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reservaService = mock(ReservaService.class);
        userContext = mock(UserContext.class);
        when(userContext.userId()).thenReturn("42");
        mvc = MockMvcBuilders.standaloneSetup(new ReservaController(reservaService, userContext))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reservar_respondeCreatedConElClienteDelContexto() throws Exception {
        when(reservaService.reservar(eq(7L), eq(42L), any(ReservaRequestDTO.class)))
                .thenReturn(ReservaResponseDTO.builder()
                        .eventoId(7L)
                        .clienteId(42L)
                        .sectorId(3L)
                        .cantidad(2)
                        .limiteEntradasPorCliente(4)
                        .entradasVigentes(2)
                        .expiraEl(Instant.now())
                        .codigosQr(List.of("qr-1", "qr-2"))
                        .build());

        mvc.perform(post("/api/tickets/eventos/7/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":3,\"cantidad\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(42))
                .andExpect(jsonPath("$.limiteEntradasPorCliente").value(4))
                .andExpect(jsonPath("$.codigosQr[0]").value("qr-1"));

        verify(reservaService).reservar(eq(7L), eq(42L), any(ReservaRequestDTO.class));
    }

    @Test
    void reservar_cuandoSuperaElLimite_responde409() throws Exception {
        when(reservaService.reservar(eq(7L), eq(42L), any(ReservaRequestDTO.class)))
                .thenThrow(new ConflictException("El cliente 42 supera el limite de 4 entradas"));

        mvc.perform(post("/api/tickets/eventos/7/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":3,\"cantidad\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail").value("El cliente 42 supera el limite de 4 entradas"));
    }

    @Test
    void reservar_conCantidadInvalida_responde400() throws Exception {
        mvc.perform(post("/api/tickets/eventos/7/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":3,\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"));
    }
}
