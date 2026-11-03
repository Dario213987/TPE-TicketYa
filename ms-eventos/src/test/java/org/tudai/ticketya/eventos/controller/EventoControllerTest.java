package org.tudai.ticketya.eventos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.service.EventoService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventoControllerTest {

    private EventoService eventoService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        eventoService = mock(EventoService.class);

        mvc = MockMvcBuilders.standaloneSetup(new EventoController(eventoService, new ModelMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void listarEventos_devuelveLaPaginaDelServicio() throws Exception {
        Evento evento = Evento.builder()
                .id(1L)
                .nombre("Festival de Verano")
                .tipo("FESTIVAL")
                .fecha(LocalDate.of(2026, 12, 1))
                .estado(EstadoEvento.CONFIRMADO)
                .build();
        Pageable pageable = PageRequest.of(0, 20, Sort.by("fecha"));
        when(eventoService.listarPublicados(pageable))
                .thenReturn(new PageImpl<>(List.of(evento), pageable, 1));

        mvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nombre").value("Festival de Verano"))
                .andExpect(jsonPath("$.content[0].tipo").value("FESTIVAL"))
                .andExpect(jsonPath("$.content[0].estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listarEventos_conSortInvalido_devuelve400() throws Exception {
        mvc.perform(get("/api/eventos").param("sort", "precio,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"));
    }
}
