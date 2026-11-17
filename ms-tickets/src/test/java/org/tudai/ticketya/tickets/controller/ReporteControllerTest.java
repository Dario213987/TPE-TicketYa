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
import org.tudai.ticketya.tickets.dto.reporte.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteEntradasSectorDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteOcupacionDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteOcupacionSectorDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteRecaudacionDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteRecaudacionSectorDTO;
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
                        .sectores(List.of(ReporteEntradasSectorDTO.builder()
                                .sectorId(10L)
                                .sectorNombre("Platea")
                                .entradasVendidas(2)
                                .entradasDevueltas(1)
                                .build()))
                        .build());

        mvc.perform(get("/api/tickets/reportes/eventos/5/entradas"))
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

        mvc.perform(get("/api/tickets/reportes/eventos/5/entradas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5));

        verify(service).obtenerReporteEntradasVendidas(5L, 99L, true);
    }

    @Test
    void obtenerReporte_conCliente_responde403() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.CLIENTE);

        mvc.perform(get("/api/tickets/reportes/eventos/5/entradas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        verifyNoInteractions(service);
    }

    @Test
    void obtenerOcupacion_conOrganizadorDuenio_respondeOkConElReporte() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(service.obtenerReporteOcupacion(5L, 42L, false))
                .thenReturn(ReporteOcupacionDTO.builder()
                        .eventoId(5L)
                        .eventoNombre("Recital")
                        .eventoFecha(FECHA_EVENTO)
                        .eventoEstado("PUBLICADO")
                        .ocupacionTotal(26.67)
                        .sectores(List.of(ReporteOcupacionSectorDTO.builder()
                                .sectorId(10L)
                                .sectorNombre("Platea")
                                .ocupacion(30.0)
                                .build()))
                        .build());

        mvc.perform(get("/api/tickets/reportes/eventos/5/ocupacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5))
                .andExpect(jsonPath("$.eventoNombre").value("Recital"))
                .andExpect(jsonPath("$.eventoFecha").value("2026-12-01T21:00:00"))
                .andExpect(jsonPath("$.eventoEstado").value("PUBLICADO"))
                .andExpect(jsonPath("$.ocupacionTotal").value(26.67))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(10))
                .andExpect(jsonPath("$.sectores[0].sectorNombre").value("Platea"))
                .andExpect(jsonPath("$.sectores[0].ocupacion").value(30.0));

        verify(service).obtenerReporteOcupacion(5L, 42L, false);
    }

    @Test
    void obtenerOcupacion_conAdmin_pasaEsAdministradorTrue() throws Exception {
        when(userContext.userId()).thenReturn(99L);
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(service.obtenerReporteOcupacion(5L, 99L, true))
                .thenReturn(ReporteOcupacionDTO.builder()
                        .eventoId(5L)
                        .eventoNombre("Recital")
                        .sectores(List.of())
                        .build());

        mvc.perform(get("/api/tickets/reportes/eventos/5/ocupacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5));

        verify(service).obtenerReporteOcupacion(5L, 99L, true);
    }

    @Test
    void obtenerOcupacion_conCliente_responde403() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.CLIENTE);

        mvc.perform(get("/api/tickets/reportes/eventos/5/ocupacion"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        verifyNoInteractions(service);
    }

    @Test
    void obtenerRecaudacion_conOrganizadorDuenio_respondeOkConElReporte() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(service.obtenerReporteRecaudacion(5L, 42L, false))
                .thenReturn(ReporteRecaudacionDTO.builder()
                        .eventoId(5L)
                        .eventoNombre("Recital")
                        .eventoFecha(FECHA_EVENTO)
                        .eventoEstado("PUBLICADO")
                        .ventas(350.0)
                        .reintegros(100.0)
                        .recaudacionTotal(250.0)
                        .sectores(List.of(ReporteRecaudacionSectorDTO.builder()
                                .sectorId(10L)
                                .sectorNombre("Platea")
                                .ventas(300.0)
                                .reintegros(100.0)
                                .recaudacion(200.0)
                                .build()))
                        .build());

        mvc.perform(get("/api/tickets/reportes/eventos/5/recaudacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5))
                .andExpect(jsonPath("$.eventoNombre").value("Recital"))
                .andExpect(jsonPath("$.eventoFecha").value("2026-12-01T21:00:00"))
                .andExpect(jsonPath("$.eventoEstado").value("PUBLICADO"))
                .andExpect(jsonPath("$.ventas").value(350.0))
                .andExpect(jsonPath("$.reintegros").value(100.0))
                .andExpect(jsonPath("$.recaudacionTotal").value(250.0))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(10))
                .andExpect(jsonPath("$.sectores[0].sectorNombre").value("Platea"))
                .andExpect(jsonPath("$.sectores[0].ventas").value(300.0))
                .andExpect(jsonPath("$.sectores[0].reintegros").value(100.0))
                .andExpect(jsonPath("$.sectores[0].recaudacion").value(200.0));

        verify(service).obtenerReporteRecaudacion(5L, 42L, false);
    }

    @Test
    void obtenerRecaudacion_conAdmin_pasaEsAdministradorTrue() throws Exception {
        when(userContext.userId()).thenReturn(99L);
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(service.obtenerReporteRecaudacion(5L, 99L, true))
                .thenReturn(ReporteRecaudacionDTO.builder()
                        .eventoId(5L)
                        .eventoNombre("Recital")
                        .sectores(List.of())
                        .build());

        mvc.perform(get("/api/tickets/reportes/eventos/5/recaudacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(5));

        verify(service).obtenerReporteRecaudacion(5L, 99L, true);
    }

    @Test
    void obtenerRecaudacion_conCliente_responde403() throws Exception {
        when(userContext.userId()).thenReturn(42L);
        when(userContext.role()).thenReturn(Role.CLIENTE);

        mvc.perform(get("/api/tickets/reportes/eventos/5/recaudacion"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        verifyNoInteractions(service);
    }
}
