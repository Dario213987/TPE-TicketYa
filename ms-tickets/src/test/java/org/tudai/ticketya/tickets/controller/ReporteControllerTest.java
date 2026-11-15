package org.tudai.ticketya.tickets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.dto.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.dto.ReporteSectorDTO;
import org.tudai.ticketya.tickets.service.ReporteService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteControllerTest {

    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2026, 12, 1, 21, 0);

    private ReporteService service;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ReporteService.class);
        userContext = mock(UserContext.class);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(new ReporteController(service, userContext))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    @Test
    void obtenerReporte_conOrganizadorDuenio_respondeOkConElReporte() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(service.obtenerReporteEntradasVendidas(5L, 42L, false))
                .thenReturn(ReporteEntradasVendidasDTO.builder()
                        .eventoId(5L)
                        .eventoNombre("Recital")
                        .eventoFecha(FECHA_EVENTO)
                        .eventoEstado("PUBLICADO")
                        .entradasVendidas(2)
                        .entradasDevueltas(1)
                        .sectores(List.of(ReporteSectorDTO.builder()
                                .sectorId(10L)
                                .sectorNombre("Platea")
                                .entradasVendidas(2)
                                .entradasDevueltas(1)
                                .build()))
                        .build());

        mvc.perform(get("/api/tickets/evento/reporte/5/entradas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5))
                .andExpect(jsonPath("$.eventoNombre").value("Recital"))
                .andExpect(jsonPath("$.eventoFecha").value("2026-12-01T21:00:00"))
                .andExpect(jsonPath("$.eventoEstado").value("PUBLICADO"))
                .andExpect(jsonPath("$.entradasVendidas").value(2))
                .andExpect(jsonPath("$.entradasDevueltas").value(1))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(10))
                .andExpect(jsonPath("$.sectores[0].sectorNombre").value("Platea"))
                .andExpect(jsonPath("$.sectores[0].entradasVendidas").value(2))
                .andExpect(jsonPath("$.sectores[0].entradasDevueltas").value(1));

        verify(service).obtenerReporteEntradasVendidas(5L, 42L, false);
    }

    @Test
    void obtenerReporte_conAdmin_pasaEsAdministradorTrue() throws Exception {
        when(userContext.userId()).thenReturn(99L);
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(service.obtenerReporteEntradasVendidas(5L, 99L, true))
                .thenReturn(ReporteEntradasVendidasDTO.builder()
                        .eventoId(5L)
                        .eventoNombre("Recital")
                        .sectores(List.of())
                        .build());

        mvc.perform(get("/api/tickets/evento/reporte/5/entradas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5));

        verify(service).obtenerReporteEntradasVendidas(5L, 99L, true);
    }

    @Test
    void obtenerReporte_conCliente_responde403() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.CLIENTE);

        mvc.perform(get("/api/tickets/evento/reporte/5/entradas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        verifyNoInteractions(service);
    }
}
