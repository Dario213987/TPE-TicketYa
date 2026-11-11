package org.tudai.ticketya.tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.InvalidUserContextException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.service.FilaService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilaControllerTest {

    private FilaService filaService;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        filaService = mock(FilaService.class);
        userContext = mock(UserContext.class);
        mvc = MockMvcBuilders.standaloneSetup(new FilaController(filaService, userContext))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    @Test
    void entrarEnFila_primeraVez_respondeCreated() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(42L);
        when(filaService.entrar(7L, 42L)).thenReturn(turno(false));

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventoId").value(7))
                .andExpect(jsonPath("$.clienteId").value(42))
                .andExpect(jsonPath("$.posicion").value(1))
                .andExpect(jsonPath("$.yaEstabaEnFila").value(false));

        verify(filaService).entrar(eq(7L), eq(42L));
    }

    @Test
    void entrarEnFila_siYaEstaba_respondeOkConLaMismaPosicion() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(42L);
        when(filaService.entrar(7L, 42L)).thenReturn(turno(true));

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posicion").value(1))
                .andExpect(jsonPath("$.yaEstabaEnFila").value(true));
    }

    @Test
    void entrarEnFila_eventoSinAltaDemanda_responde409() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(42L);
        when(filaService.entrar(7L, 42L))
                .thenThrow(new ConflictException("El evento 7 no es de alta demanda"));

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("El evento 7 no es de alta demanda"));
    }

    @Test
    void entrarEnFila_eventoConVentaCerrada_responde409() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(42L);
        when(filaService.entrar(7L, 42L))
                .thenThrow(new ConflictException(
                        "La venta del evento 7 no está abierta (SUSPENDIDA)"));

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void entrarEnFila_eventoSinDisponibilidad_responde404() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(42L);
        when(filaService.entrar(7L, 42L))
                .thenThrow(new NotFoundException("No hay disponibilidad para el evento 7"));

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void entrarEnFila_sinUsuarioAutenticado_responde401() throws Exception {
        when(userContext.userId()).thenThrow(
                new InvalidUserContextException("Falta el header obligatorio X-User-Id"));

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(filaService);
    }

    @Test
    void entrarEnFila_conRolOrganizador_responde403() throws Exception {
        when(userContext.userId()).thenReturn(1L);
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);

        mvc.perform(post("/api/tickets/eventos/7/fila"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(filaService);
    }

    private TurnoFilaResponseDTO turno(boolean yaEstaba) {
        return TurnoFilaResponseDTO.builder()
                .eventoId(7L)
                .clienteId(42L)
                .posicion(1L)
                .totalEnFila(1L)
                .yaEstabaEnFila(yaEstaba)
                .ingresadoEl(Instant.now())
                .build();
    }
}
