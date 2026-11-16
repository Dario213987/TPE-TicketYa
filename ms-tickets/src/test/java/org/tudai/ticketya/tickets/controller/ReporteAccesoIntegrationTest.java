package org.tudai.ticketya.tickets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.client.EventoClient;
import org.tudai.ticketya.tickets.client.dto.EventoResponseDto;
import org.tudai.ticketya.tickets.client.dto.SectorResponseDto;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.ReporteRepository;
import org.tudai.ticketya.tickets.service.EventoAccessValidator;
import org.tudai.ticketya.tickets.service.ReporteService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteAccesoIntegrationTest {

    private static final Long EVENTO_ID = 5L;
    private static final Long SOLICITANTE_ID = 42L;
    private static final Long ORGANIZADOR_EVENTO_ID = 99L;

    private EventoClient eventoClient;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        eventoClient = mock(EventoClient.class);
        userContext = mock(UserContext.class);

        ReporteService service = new ReporteService(
                eventoClient,
                new EventoAccessValidator(),
                mock(ReporteRepository.class),
                mock(DisponibilidadSectorRepository.class));

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(new ReporteController(service, userContext))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();

        when(userContext.userId()).thenReturn(SOLICITANTE_ID);
        when(eventoClient.obtenerEvento(EVENTO_ID)).thenReturn(evento());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entradas", "ocupacion", "recaudacion"})
    void reporte_cuandoElOrganizadorNoEsDuenio_responde403(String reporte) throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);

        mvc.perform(get("/api/tickets/reportes/eventos/{id}/{reporte}", EVENTO_ID, reporte))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"entradas", "ocupacion", "recaudacion"})
    void reporte_cuandoEsAdmin_permiteEventoAjeno(String reporte) throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);

        mvc.perform(get("/api/tickets/reportes/eventos/{id}/{reporte}", EVENTO_ID, reporte))
                .andExpect(status().isOk());
    }

    @Test
    void reporte_cuandoElEventoNoExiste_responde404() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(eventoClient.obtenerEvento(EVENTO_ID)).thenReturn(null);

        mvc.perform(get("/api/tickets/reportes/eventos/{id}/entradas", EVENTO_ID))
                .andExpect(status().isNotFound());
    }

    private EventoResponseDto evento() {
        return EventoResponseDto.builder()
                .id(EVENTO_ID)
                .nombre("Recital")
                .fecha(LocalDateTime.of(2026, 12, 1, 21, 0))
                .estado("PUBLICADO")
                .organizadorId(ORGANIZADOR_EVENTO_ID)
                .sectores(List.of(SectorResponseDto.builder()
                        .id(10L)
                        .nombre("Platea")
                        .build()))
                .build();
    }
}
