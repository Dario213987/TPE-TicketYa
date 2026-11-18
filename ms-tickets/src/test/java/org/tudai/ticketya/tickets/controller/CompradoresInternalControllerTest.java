package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.tickets.service.CompradoresService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompradoresInternalControllerTest {

    private CompradoresService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CompradoresService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CompradoresInternalController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listarCompradores_devuelveLosIdsDistintos() throws Exception {
        when(service.listarCompradores(5L)).thenReturn(List.of(7L, 42L));

        mvc.perform(get("/api/tickets/internal/eventos/5/compradores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(7))
                .andExpect(jsonPath("$[1]").value(42));

        verify(service).listarCompradores(5L);
    }

    @Test
    void listarCompradores_cuandoNoHayCompras_devuelveListaVacia() throws Exception {
        when(service.listarCompradores(99L)).thenReturn(List.of());

        mvc.perform(get("/api/tickets/internal/eventos/99/compradores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).listarCompradores(99L);
    }
}
