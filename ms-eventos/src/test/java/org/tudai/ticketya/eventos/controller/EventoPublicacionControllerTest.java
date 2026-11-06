package org.tudai.ticketya.eventos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.eventos.dto.evento.EventoPublicadoDTO;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.service.EventoService;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventoPublicacionControllerTest {

    private EventoService eventoService;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        eventoService = mock(EventoService.class);
        userContext = mock(UserContext.class);

        mvc = mockMvc(new org.tudai.ticketya.web.error.GlobalExceptionHandler());
    }

    @Test
    void publicarEvento_comoOrganizador_devuelve200() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenReturn(eventoPublicado());

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(eventoService).publicarEvento(1L, 7L, false);
    }

    @Test
    void publicarEvento_comoAdministrador_invocaServicioComoAdmin() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, true))
                .thenReturn(eventoPublicado());

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(eventoService).publicarEvento(1L, 7L, true);
    }

    @Test
    void publicarEvento_comoCliente_devuelve403SinLlamarAlServicio() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(7L);

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventoService);
    }

    @Test
    void publicarEvento_conSectoresSinPrecio_devuelve409ConLosSectores() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new SectoresSinPrecioException(List.of(
                        new SectoresSinPrecioException.SectorFaltante(10L, "Platea"),
                        new SectoresSinPrecioException.SectorFaltante(20L, "Campo"))));

        MockMvc eventosMvc = mockMvc(new org.tudai.ticketya.eventos.config.EventosExceptionHandler());

        eventosMvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("No se puede publicar el evento"))
                .andExpect(jsonPath("$.detail").value("Los siguientes sectores no tienen precio: Platea, Campo"))
                .andExpect(jsonPath("$.sectores[0].id").value(10))
                .andExpect(jsonPath("$.sectores[1].id").value(20));

        verify(eventoService).publicarEvento(1L, 7L, false);
    }

    @Test
    void publicarEvento_conEventoInexistente_devuelve404() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new NotFoundException("Evento 1 no existe"));

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Evento 1 no existe"));
    }

    @Test
    void publicarEvento_conEventoInvalido_devuelve400() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new BadRequestException("La fecha del evento no es valida"));

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha del evento no es valida"));
    }

    @Test
    void publicarEvento_conMsTicketsNoDisponible_devuelve502() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new RemoteServiceException(502, "ms-tickets no disponible"));

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Error en servicio dependiente"));
    }

    private MockMvc mockMvc(Object... controllerAdvice) {
        return MockMvcBuilders.standaloneSetup(new EventoController(eventoService, new ModelMapper(), userContext))
                .setControllerAdvice(controllerAdvice)
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    private EventoPublicadoDTO eventoPublicado() {
        return EventoPublicadoDTO.builder()
                .id(1L)
                .nombre("Festival")
                .estado("PUBLICADO")
                .build();
    }
}
