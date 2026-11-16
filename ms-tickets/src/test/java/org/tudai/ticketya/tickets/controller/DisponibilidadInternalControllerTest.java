package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.tickets.service.DisponibilidadService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisponibilidadInternalControllerTest {

    private DisponibilidadService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DisponibilidadService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DisponibilidadInternalController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void actualizarAltaDemanda_actualizaElFlag() throws Exception {
        mvc.perform(put("/api/tickets/internal/eventos/5/alta-demanda")
                        .param("altaDemanda", "true"))
                .andExpect(status().isNoContent());

        verify(service).actualizarAltaDemanda(5L, true);
    }
}
