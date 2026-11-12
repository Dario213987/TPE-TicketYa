package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.MiEntradaDTO;
import org.tudai.ticketya.tickets.dto.QrEntradaDTO;
import org.tudai.ticketya.tickets.service.EntradaService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntradaControllerTest {

    private EntradaService entradaService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        entradaService = mock(EntradaService.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.userId()).thenReturn(42L);
        mvc = MockMvcBuilders.standaloneSetup(new EntradaController(entradaService, userContext))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void misEntradas_listaLasDelClienteDelContexto() throws Exception {
        when(entradaService.listar(eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(MiEntradaDTO.builder()
                        .id(1L).eventoId(7L).sectorId(3L).fila(2).numero(5)
                        .estado(EstadoTicket.VENDIDO).utilizado(false).precio(1500.0)
                        .build()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/tickets/mis-entradas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].eventoId").value(7))
                .andExpect(jsonPath("$.content[0].fila").value(2))
                .andExpect(jsonPath("$.content[0].estado").value("VENDIDO"))
                .andExpect(jsonPath("$.content[0].utilizado").value(false))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(entradaService).listar(eq(42L), any(Pageable.class));
    }

    @Test
    void qr_devuelveElContenidoDeLaEntrada() throws Exception {
        when(entradaService.qr(1L, 42L))
                .thenReturn(QrEntradaDTO.builder().ticketId(1L).contenido("qr-1").build());

        mvc.perform(get("/api/tickets/1/qr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(1))
                .andExpect(jsonPath("$.contenido").value("qr-1"));
    }

    @Test
    void qr_entradaDeOtroCliente_respondeNotFound() throws Exception {
        when(entradaService.qr(1L, 42L)).thenThrow(new NotFoundException("No existe la entrada 1"));

        mvc.perform(get("/api/tickets/1/qr"))
                .andExpect(status().isNotFound());
    }
}
