package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.dto.DevolucionTicketResponseDTO;
import org.tudai.ticketya.tickets.dto.EntradaDetalleDTO;
import org.tudai.ticketya.tickets.service.TicketDevolucionService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntradaInternalControllerTest {

    private TicketDevolucionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(TicketDevolucionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EntradaInternalController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST devolver responde 200 con DTO de resultado")
    void devolverTicket_responde200() throws Exception {
        DevolucionTicketResponseDTO resultado = DevolucionTicketResponseDTO.builder()
                .ticketId(100L)
                .estado("DEVUELTO")
                .transaccionId(999L)
                .reintegroPendiente(false)
                .build();

        when(service.devolverTicket(100L)).thenReturn(resultado);

        mvc.perform(post("/api/tickets/internal/entradas/100/devolver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(100))
                .andExpect(jsonPath("$.estado").value("DEVUELTO"))
                .andExpect(jsonPath("$.transaccionId").value(999))
                .andExpect(jsonPath("$.reintegroPendiente").value(false));

        verify(service).devolverTicket(100L);
    }

    @Test
    @DisplayName("POST devolver cuando ya devuelto responde 409")
    void devolverTicket_yaDevuelto_responde409() throws Exception {
        when(service.devolverTicket(100L))
                .thenThrow(new ConflictException("El ticket ya fue devuelto"));

        mvc.perform(post("/api/tickets/internal/entradas/100/devolver"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST devolver cuando no existe responde 404")
    void devolverTicket_noExiste_responde404() throws Exception {
        when(service.devolverTicket(999L))
                .thenThrow(new NotFoundException("No existe el ticket 999"));

        mvc.perform(post("/api/tickets/internal/entradas/999/devolver"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET detalle responde 200")
    void obtenerDetalleEntrada_responde200() throws Exception {
        EntradaDetalleDTO detalle = EntradaDetalleDTO.builder()
                .id(100L)
                .eventoId(10L)
                .precio(1500.0)
                .clienteId(20L)
                .estado("EMITIDO")
                .utilizado(false)
                .build();

        when(service.obtenerDetalle(100L)).thenReturn(detalle);

        mvc.perform(get("/api/tickets/internal/entradas/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.precio").value(1500.0))
                .andExpect(jsonPath("$.estado").value("EMITIDO"));
    }
}
