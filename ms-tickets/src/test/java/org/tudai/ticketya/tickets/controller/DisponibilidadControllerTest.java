package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.dto.AsientoDisponibilidadDTO;
import org.tudai.ticketya.tickets.dto.DisponibilidadEventoResponseDTO;
import org.tudai.ticketya.tickets.dto.SectorDisponibilidadResponseDTO;
import org.tudai.ticketya.tickets.service.DisponibilidadService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisponibilidadControllerTest {

    private DisponibilidadService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DisponibilidadService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DisponibilidadController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void consultarDisponibilidad_respondeOkConElCuerpoDelServicio() throws Exception {
        when(service.consultar(5L)).thenReturn(DisponibilidadEventoResponseDTO.builder()
                .eventoId(5L)
                .sectores(List.of(SectorDisponibilidadResponseDTO.builder()
                        .sectorId(2L)
                        .tipoSector(TipoSector.NUMERADO)
                        .capacidadTotal(2)
                        .disponibles(1)
                        .asientos(List.of(AsientoDisponibilidadDTO.builder()
                                .fila(1)
                                .numero(1)
                                .estado(EstadoDisponibilidad.LIBRE)
                                .build()))
                        .build()))
                .build());

        mvc.perform(get("/api/tickets/eventos/5/disponibilidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(2))
                .andExpect(jsonPath("$.sectores[0].tipoSector").value("NUMERADO"))
                .andExpect(jsonPath("$.sectores[0].capacidadTotal").value(2))
                .andExpect(jsonPath("$.sectores[0].disponibles").value(1))
                .andExpect(jsonPath("$.sectores[0].asientos[0].fila").value(1))
                .andExpect(jsonPath("$.sectores[0].asientos[0].numero").value(1))
                .andExpect(jsonPath("$.sectores[0].asientos[0].estado").value("LIBRE"));
    }

    @Test
    void consultarDisponibilidad_usaElEventoIdDelPath() throws Exception {
        when(service.consultar(42L)).thenReturn(DisponibilidadEventoResponseDTO.builder()
                .eventoId(42L)
                .sectores(List.of())
                .build());

        mvc.perform(get("/api/tickets/eventos/42/disponibilidad"))
                .andExpect(status().isOk());

        verify(service).consultar(42L);
    }

    @Test
    void consultarDisponibilidad_cuandoNoExiste_responde404ConFormatoEstandar() throws Exception {
        when(service.consultar(99L))
                .thenThrow(new NotFoundException("No hay disponibilidad para el evento 99"));

        mvc.perform(get("/api/tickets/eventos/99/disponibilidad"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("No hay disponibilidad para el evento 99"));
    }
}
