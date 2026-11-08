package org.tudai.ticketya.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;
import org.tudai.ticketya.eventos.repository.ParticipanteRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
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
        mvc.perform(crearEvento(LocalDate.now().plusDays(10), """
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
        mvc.perform(crearEvento(LocalDate.now().minusDays(1), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha del evento debe ser posterior a hoy"));

        assertThat(eventoRepository.count()).isZero();
    }

    @Test
    void crearEventoBorrador_conLugarInexistente_devuelve404() throws Exception {
        mvc.perform(crearEvento(LocalDate.now().plusDays(1), "", 999999L))
                .andExpect(status().isNotFound());

        assertThat(eventoRepository.count()).isZero();
    }

    @Test
    void crearEventoBorrador_resuelveParticipantesExistentesYNuevos() throws Exception {
        Participante existente = participanteRepository.save(
                Participante.builder().nombre("Artista existente").build());

        mvc.perform(crearEvento(LocalDate.now().plusDays(3), """
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
                                """.formatted(LocalDate.now().plusDays(2), lugar.getId())))
                .andExpect(status().isBadRequest());

        assertThat(eventoRepository.count()).isZero();
    }

    private MockHttpServletRequestBuilder crearEvento(LocalDate fecha, String participantes) {
        return crearEvento(fecha, participantes, lugar.getId());
    }

    private MockHttpServletRequestBuilder crearEvento(LocalDate fecha, String participantes, Long lugarId) {
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
