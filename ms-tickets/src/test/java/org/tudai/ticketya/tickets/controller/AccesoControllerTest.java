package org.tudai.ticketya.tickets.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.OperationLogger;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.InvalidUserContextException;
import org.tudai.ticketya.tickets.config.AccesoExceptionHandler;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.exception.AccesoRechazadoException;
import org.tudai.ticketya.tickets.exception.CodigoRechazoAcceso;
import org.tudai.ticketya.tickets.service.AccesoService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccesoControllerTest {

    private AccesoService service;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AccesoService.class);
        userContext = mock(UserContext.class);
        mvc = MockMvcBuilders.standaloneSetup(new AccesoController(service, userContext))
                .setControllerAdvice(new AccesoExceptionHandler(), new GlobalExceptionHandler())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    @Test
    void validar_conPersonalDeAcceso_respondeOkConElResultado() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1")).thenReturn(ResultadoAccesoDTO.builder()
                .resultado("OK")
                .valido(true)
                .codigoQr("qr-1")
                .eventoId(5L)
                .estadoTicket(EstadoTicket.USADO)
                .ticketId(99L)
                .build());

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"qr-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("OK"))
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.codigoQr").value("qr-1"))
                .andExpect(jsonPath("$.eventoId").value(5))
                .andExpect(jsonPath("$.estadoTicket").value("USADO"));

        verify(service).validar(5L, "qr-1");
    }

    @Test
    void validar_exitoso_registraLaOperacion() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1")).thenReturn(ResultadoAccesoDTO.builder()
                .resultado("OK")
                .valido(true)
                .codigoQr("qr-1")
                .eventoId(5L)
                .estadoTicket(EstadoTicket.USADO)
                .ticketId(99L)
                .build());

        List<ILoggingEvent> operaciones = capturarOperaciones(() ->
                mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigoQr\":\"qr-1\"}"))
                        .andExpect(status().isOk()));

        assertThat(operaciones).singleElement().satisfies(event ->
                assertThat(event.getMDCPropertyMap())
                        .containsEntry("operation", "ACCESS")
                        .containsEntry("result", "OK")
                        .containsEntry("userId", "7")
                        .containsEntry("entity", "Ticket")
                        .containsEntry("entityId", "99")
                        .doesNotContainValue("qr-1"));
    }

    @Test
    void validar_fallido_registraLaOperacionConElMotivo() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1"))
                .thenThrow(new AccesoRechazadoException(
                        CodigoRechazoAcceso.YA_USADA,
                        "El ticket ya fue utilizado",
                        99L));

        List<ILoggingEvent> operaciones = capturarOperaciones(() ->
                mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigoQr\":\"qr-1\"}"))
                        .andExpect(status().isConflict()));

        assertThat(operaciones).singleElement().satisfies(event ->
                assertThat(event.getMDCPropertyMap())
                        .containsEntry("operation", "ACCESS")
                        .containsEntry("result", "FAILED")
                        .containsEntry("userId", "7")
                        .containsEntry("entity", "Ticket")
                        .containsEntry("entityId", "99")
                        .containsEntry("reason", "El ticket ya fue utilizado")
                        .doesNotContainValue("qr-1"));
    }

    @Test
    void validar_conRolCliente_responde403() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.CLIENTE);

        List<ILoggingEvent> operaciones = capturarOperaciones(() ->
                mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigoQr\":\"qr-1\"}"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.title").value("Acceso denegado")));

        verifyNoInteractions(service);
        assertThat(operaciones).isEmpty();
    }

    @Test
    void validar_sinUsuario_responde401() throws Exception {
        when(userContext.userId())
                .thenThrow(new InvalidUserContextException("Falta el header X-User-Id"));

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"qr-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));

        verifyNoInteractions(service);
    }

    @Test
    void validar_qrVacio_responde400() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void validar_qrInexistente_responde404ConCodigoNoExiste() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1"))
                .thenThrow(new AccesoRechazadoException(
                        CodigoRechazoAcceso.NO_EXISTE,
                        "No existe un ticket con ese codigo QR"));

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"qr-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.codigo").value("NO_EXISTE"))
                .andExpect(jsonPath("$.detail").value("No existe un ticket con ese codigo QR"));
    }

    @Test
    void validar_ticketYaUsado_responde409ConCodigoYaUsada() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1"))
                .thenThrow(new AccesoRechazadoException(
                        CodigoRechazoAcceso.YA_USADA,
                        "El ticket ya fue utilizado"));

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"qr-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.codigo").value("YA_USADA"))
                .andExpect(jsonPath("$.detail").value("El ticket ya fue utilizado"));
    }

    @Test
    void validar_ticketDeOtroEvento_responde409ConCodigoOtroEvento() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1"))
                .thenThrow(new AccesoRechazadoException(
                        CodigoRechazoAcceso.OTRO_EVENTO,
                        "El ticket no pertenece al evento indicado"));

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"qr-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OTRO_EVENTO"))
                .andExpect(jsonPath("$.detail").value("El ticket no pertenece al evento indicado"));
    }

    @Test
    void validar_ticketDevuelto_responde409ConCodigoDevuelta() throws Exception {
        when(userContext.userId()).thenReturn(7L);
        when(userContext.role()).thenReturn(Role.PERSONAL_ACCESO);
        when(service.validar(5L, "qr-1"))
                .thenThrow(new AccesoRechazadoException(
                        CodigoRechazoAcceso.DEVUELTA,
                        "El ticket fue devuelto"));

        mvc.perform(post("/api/tickets/eventos/5/accesos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoQr\":\"qr-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DEVUELTA"))
                .andExpect(jsonPath("$.detail").value("El ticket fue devuelto"));
    }

    private List<ILoggingEvent> capturarOperaciones(ThrowingRunnable request) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(OperationLogger.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            request.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
