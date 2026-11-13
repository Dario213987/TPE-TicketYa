package org.tudai.ticketya.eventos;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.eventos.client.DevolucionesClient;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;
import org.tudai.ticketya.eventos.repository.ParticipanteRepository;
import org.tudai.ticketya.eventos.service.EventoService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:eventos-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@AutoConfigureMockMvc
class EventoApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    LugarRepository lugarRepository;

    @Autowired
    ParticipanteRepository participanteRepository;

    @Autowired
    EventoService eventoService;

    @MockitoBean
    TicketsClient ticketsClient;

    @MockitoBean
    DevolucionesClient devolucionesClient;

    private Lugar lugar;

    @BeforeEach
    void prepararBaseAislada() {
        eventoRepository.deleteAll();
        participanteRepository.deleteAll();
        lugarRepository.deleteAll();
        lugar = lugarRepository.save(Lugar.builder()
                .nombre("Lugar de integración")
                .tipo("TEATRO")
                .direccion("Calle de prueba 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build());
    }

    @Test
    void crearEventoBorrador_devuelve201ConEstadoYOrganizadorYNoSeLista() throws Exception {
        mvc.perform(crearEvento(LocalDateTime.now().plusDays(10), """
                "participantes": []
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.organizadorId").value(7))
                .andExpect(jsonPath("$.tipo").value("RECITAL"))
                .andExpect(jsonPath("$.sectores").isEmpty())
                .andExpect(jsonPath("$.preciosSectores").isEmpty());

        assertThat(eventoRepository.count()).isEqualTo(1);
        mvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void crearEventoBorrador_conFechaPasada_devuelve400() throws Exception {
        mvc.perform(crearEvento(LocalDateTime.now().minusDays(1), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha del evento debe ser posterior a hoy"));

        assertThat(eventoRepository.count()).isZero();
    }

    @Test
    void crearEventoBorrador_conLugarInexistente_devuelve404() throws Exception {
        mvc.perform(crearEvento(LocalDateTime.now().plusDays(1), "", 999999L))
                .andExpect(status().isNotFound());

        assertThat(eventoRepository.count()).isZero();
    }

    @Test
    void crearEventoBorrador_resuelveParticipantesExistentesYNuevos() throws Exception {
        Participante existente = participanteRepository.save(
                Participante.builder().nombre("Artista existente").build());

        mvc.perform(crearEvento(LocalDateTime.now().plusDays(3), """
                "participantes": [
                  {"id": %d},
                  {"nombre": "Artista nuevo"}
                ]
                """.formatted(existente.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantes.length()").value(2))
                .andExpect(jsonPath("$.participantes[0].nombre").value("Artista existente"))
                .andExpect(jsonPath("$.participantes[1].nombre").value("Artista nuevo"));

        assertThat(participanteRepository.count()).isEqualTo(2);
    }

    @Test
    void crearEventoBorrador_conTipoFueraDelEnum_devuelve400() throws Exception {
        mvc.perform(post("/api/eventos")
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "ORGANIZADOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Evento inválido",
                                  "descripcion": "Prueba",
                                  "tipo": "NO_EXISTE",
                                  "fecha": "%s",
                                  "lugarId": %d
                                }
                                """.formatted(LocalDateTime.now().plusDays(2), lugar.getId())))
                .andExpect(status().isBadRequest());

        assertThat(eventoRepository.count()).isZero();
    }

    @Test
    void listarEventos_soloDevuelvePublicadosYAgotados() throws Exception {
        for (EstadoEvento estado : EstadoEvento.values()) {
            eventoRepository.save(Evento.builder()
                    .nombre("Evento " + estado)
                    .descripcion("Listado por estado")
                    .tipo(TipoEvento.RECITAL)
                    .fecha(LocalDateTime.now().plusDays(10))
                    .lugar(lugar)
                    .organizadorId(7L)
                    .estado(estado)
                    .build());
        }

        mvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].estado",
                        containsInAnyOrder("PUBLICADO", "AGOTADO")));
    }

    @Test
    void listarEventos_filtraPorTipoCiudadYOrganizador() throws Exception {
        Lugar otroLugar = guardarLugar("Mar del Plata");
        guardarEvento("Recital Tandil", TipoEvento.RECITAL, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 5, 20, 0));
        guardarEvento("Festival Tandil", TipoEvento.FESTIVAL, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 6, 20, 0));
        guardarEvento("Recital Mar del Plata", TipoEvento.RECITAL, EstadoEvento.PUBLICADO, 7L,
                otroLugar, LocalDateTime.of(2026, 12, 7, 20, 0));
        guardarEvento("Recital de otro organizador", TipoEvento.RECITAL, EstadoEvento.PUBLICADO, 9L,
                lugar, LocalDateTime.of(2026, 12, 8, 20, 0));

        mvc.perform(get("/api/eventos")
                        .param("tipo", " recital ")
                        .param("ciudad", "TANDIL")
                        .param("organizadorId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Recital Tandil"));
    }

    @Test
    void listarEventos_filtraPorParticipanteSinTildesYNoDuplica() throws Exception {
        Participante conTilde = Participante.builder().nombre("Café Tacvba").build();
        Participante sinTilde = Participante.builder().nombre("Cafe Tacvba").build();
        guardarEvento("Festival Cafe", TipoEvento.FESTIVAL, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 10, 20, 0), conTilde, sinTilde);
        guardarEvento("Recital Soda", TipoEvento.RECITAL, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 11, 20, 0),
                Participante.builder().nombre("Soda Stereo").build());

        mvc.perform(get("/api/eventos").param("participante", "CAFE TACVBA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Festival Cafe"));
    }

    @Test
    void listarEventos_filtraPorRangoDeFechasInclusivo() throws Exception {
        guardarEvento("Primer día", TipoEvento.RECITAL, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 1, 20, 0));
        guardarEvento("Último día", TipoEvento.FESTIVAL, EstadoEvento.AGOTADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 31, 23, 30));
        guardarEvento("Fuera de rango", TipoEvento.TEATRO, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2027, 1, 1, 0, 30));

        mvc.perform(get("/api/eventos")
                        .param("fechaDesde", "2026-12-01")
                        .param("fechaHasta", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].nombre").value("Primer día"))
                .andExpect(jsonPath("$.content[1].nombre").value("Último día"));
    }

    @Test
    void listarEventos_conRangoInvertido_devuelve400() throws Exception {
        mvc.perform(get("/api/eventos")
                        .param("fechaDesde", "2026-12-31")
                        .param("fechaHasta", "2026-12-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("La fecha 'desde' no puede ser posterior a la fecha 'hasta'"));
    }

    @Test
    void listarEventos_conTipoInvalido_devuelve400() throws Exception {
        mvc.perform(get("/api/eventos").param("tipo", "INVALIDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Tipo de evento inválido: 'INVALIDO'. Valores válidos: RECITAL, FESTIVAL, TEATRO, DEPORTE, CONFERENCIA, OTRO"));
    }

    @Test
    void listarEventos_participanteParcial_noDevuelveCoincidencias() throws Exception {
        guardarEvento("Recital Soda", TipoEvento.RECITAL, EstadoEvento.PUBLICADO, 7L,
                lugar, LocalDateTime.of(2026, 12, 11, 20, 0),
                Participante.builder().nombre("Soda Stereo").build());

        mvc.perform(get("/api/eventos").param("participante", "Soda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void listarEventos_conSizeMayorA50_devuelve400() throws Exception {
        mvc.perform(get("/api/eventos").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El tamaño de página no puede superar 50"));
    }

    @Test
    void cancelarEvento_cuandoMsTicketsFalla_noPersisteElCambioDeEstado() {
        Evento evento = eventoRepository.save(Evento.builder()
                .nombre("Festival de integración")
                .fecha(LocalDateTime.now().plusDays(10))
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(7L)
                .lugar(lugar)
                .build());
        doThrow(mock(FeignException.class)).when(ticketsClient).cancelarEvento(evento.getId());

        assertThatThrownBy(() -> eventoService.cancelarEvento(evento.getId()))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets respondió con error");

        assertThat(eventoRepository.findById(evento.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoEvento.PUBLICADO);
    }

    private Lugar guardarLugar(String ciudad) {
        return lugarRepository.save(Lugar.builder()
                .nombre("Lugar de " + ciudad)
                .tipo("TEATRO")
                .direccion("Calle de prueba 123")
                .ciudad(ciudad)
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build());
    }

    private Evento guardarEvento(String nombre, TipoEvento tipo, EstadoEvento estado, Long organizadorId,
                                 Lugar lugar, LocalDateTime fecha, Participante... participantes) {
        Evento evento = Evento.builder()
                .nombre(nombre)
                .tipo(tipo)
                .estado(estado)
                .organizadorId(organizadorId)
                .lugar(lugar)
                .fecha(fecha)
                .build();
        for (Participante participante : participantes) {
            evento.addParticipante(participante);
        }
        return eventoRepository.save(evento);
    }

    private MockHttpServletRequestBuilder crearEvento(LocalDateTime fecha, String participantes) {
        return crearEvento(fecha, participantes, lugar.getId());
    }

    private MockHttpServletRequestBuilder crearEvento(LocalDateTime fecha, String participantes, Long lugarId) {
        return post("/api/eventos")
                .header("X-User-Id", "7")
                .header("X-User-Role", "ORGANIZADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Recital de prueba",
                          "descripcion": "Evento de integración",
                          "tipo": "RECITAL",
                          "fecha": "%s",
                          "lugarId": %d
                          %s
                        }
                        """.formatted(fecha, lugarId,
                        participantes.isBlank() ? "" : ",\n" + participantes));
    }
}
