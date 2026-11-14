package org.tudai.ticketya.eventos.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.dto.evento.EventoFiltroRequestDTO;
import org.tudai.ticketya.eventos.service.EventoService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.password=ignored",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@Testcontainers
class EventoFiltroMySQLTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    EventoService eventoService;

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    LugarRepository lugarRepository;

    @Autowired
    ParticipanteRepository participanteRepository;

    @BeforeEach
    void limpiarBase() {
        eventoRepository.deleteAll();
        participanteRepository.deleteAll();
        lugarRepository.deleteAll();
    }

    @Test
    void filtroParticipanteFuncionaEnMySQLSinFuncionesDeOtroMotor() {
        Lugar lugar = lugarRepository.save(Lugar.builder()
                .nombre("Lugar de prueba")
                .tipo("TEATRO")
                .direccion("Calle de prueba 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build());
        Participante participante = Participante.builder().nombre("Café Tacvba").build();
        Evento evento = Evento.builder()
                .nombre("Festival Cafe")
                .tipo(TipoEvento.FESTIVAL)
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(7L)
                .lugar(lugar)
                .fecha(LocalDateTime.now().plusDays(10))
                .build();
        evento.addParticipante(participante);
        eventoRepository.saveAndFlush(evento);

        Page<Evento> resultado = eventoService.listarPublicados(
                EventoFiltroRequestDTO.builder().participante("CAFE TACVBA").build(),
                PageRequest.of(0, 20));

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).getNombre()).isEqualTo("Festival Cafe");
    }

    @Test
    void ordenPrecioFuncionaEnMySQLUsandoLosPreciosDelEvento() {
        Lugar lugarBarato = guardarLugar("lugar-barato", 10.0);
        Lugar lugarCaro = guardarLugar("lugar-caro", 1000.0);

        Evento eventoCaro = guardarEvento("evento-caro", lugarBarato, 500.0);
        Evento eventoBarato = guardarEvento("evento-barato", lugarCaro, 100.0);

        Page<Evento> resultado = eventoService.listarPublicados(
                EventoFiltroRequestDTO.builder().orden("precio").build(),
                PageRequest.of(0, 20));

        assertThat(resultado.getContent())
                .extracting(Evento::getNombre)
                .containsExactly(eventoBarato.getNombre(), eventoCaro.getNombre());
    }

    private Lugar guardarLugar(String nombre, double precio) {
        Lugar lugar = Lugar.builder()
                .nombre(nombre)
                .tipo("TEATRO")
                .direccion("Calle de prueba 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build();
        lugar.addSector(SectorNoNumerado.builder()
                .nombre("General")
                .precio(precio)
                .capacidadMaxima(100)
                .build());
        return lugarRepository.saveAndFlush(lugar);
    }

    private Evento guardarEvento(String nombre, Lugar lugar, double precio) {
        Evento evento = Evento.builder()
                .nombre(nombre)
                .tipo(TipoEvento.FESTIVAL)
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(7L)
                .lugar(lugar)
                .fecha(LocalDateTime.now().plusDays(10))
                .build();
        evento.definirPrecioSector(lugar.getSectores().iterator().next().getId(), precio);
        return eventoRepository.saveAndFlush(evento);
    }
}
