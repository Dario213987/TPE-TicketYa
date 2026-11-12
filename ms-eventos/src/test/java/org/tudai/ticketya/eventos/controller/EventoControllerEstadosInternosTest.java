package org.tudai.ticketya.eventos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoEstadoResponse;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.service.EventoService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventoControllerEstadosInternosTest {

    private EventoService eventoService;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        eventoService = mock(EventoService.class);
        userContext = mock(UserContext.class);

        mvc = MockMvcBuilders.standaloneSetup(new EventoController(eventoService, new ModelMapper(), userContext))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    @Test
    void marcarAgotado_devuelve200() throws Exception {
        when(eventoService.marcarAgotado(1L)).thenReturn(eventoEnEstado(EstadoEvento.AGOTADO));

        mvc.perform(put("/api/eventos/internal/1/estado/agotado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("AGOTADO"));

        verify(eventoService).marcarAgotado(1L);
    }

    @Test
    void marcarPublicado_devuelve200() throws Exception {
        when(eventoService.marcarPublicado(1L)).thenReturn(eventoEnEstado(EstadoEvento.PUBLICADO));

        mvc.perform(put("/api/eventos/internal/1/estado/publicar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(eventoService).marcarPublicado(1L);
    }

    @Test
    void finalizarVencidos_devuelve204() throws Exception {
        mvc.perform(put("/api/eventos/internal/finalizar-vencidos"))
                .andExpect(status().isNoContent());

        verify(eventoService).finalizarEventosVencidos();
    }

    @Test
    void marcarAgotado_enEstadoNoPublicado_devuelve409() throws Exception {
        when(eventoService.marcarAgotado(1L))
                .thenThrow(new ConflictException("Solo un evento publicado puede pasar a agotado"));

        mvc.perform(put("/api/eventos/internal/1/estado/agotado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo un evento publicado puede pasar a agotado"));
    }

    @Test
    void marcarPublicado_enEstadoNoAgotado_devuelve409() throws Exception {
        when(eventoService.marcarPublicado(1L))
                .thenThrow(new ConflictException("Solo un evento agotado puede pasar a publicado"));

        mvc.perform(put("/api/eventos/internal/1/estado/publicar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo un evento agotado puede pasar a publicado"));
    }

    @Test
    void marcarAgotado_conEventoInexistente_devuelve404() throws Exception {
        when(eventoService.marcarAgotado(99L))
                .thenThrow(new NotFoundException("Evento no encontrado con id: 99"));

        mvc.perform(put("/api/eventos/internal/99/estado/agotado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Evento no encontrado con id: 99"));
    }

    @Test
    void marcarPublicado_conEventoInexistente_devuelve404() throws Exception {
        when(eventoService.marcarPublicado(99L))
                .thenThrow(new NotFoundException("Evento no encontrado con id: 99"));

        mvc.perform(put("/api/eventos/internal/99/estado/publicar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Evento no encontrado con id: 99"));
    }

    private EventoEstadoResponse eventoEnEstado(EstadoEvento estado) {
        return new EventoEstadoResponse(1L, "Festival", estado);
    }
}
