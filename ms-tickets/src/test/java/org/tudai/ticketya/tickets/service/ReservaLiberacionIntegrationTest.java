package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.domain.DisponibilidadAsiento;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Reserva;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-liberacion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "transacciones.name=transacciones",
        "tickets.reserva.liberacion-cron=0 0 0 1 1 *"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservaLiberacionIntegrationTest {

    @Autowired
    private ReservaVencidaService reservaVencidaService;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private BloqueoDistribuidoService bloqueoDistribuidoService;

    @Autowired
    private DisponibilidadEventoRepository eventoRepository;

    @Autowired
    private DisponibilidadSectorRepository sectorRepository;

    @Autowired
    private DisponibilidadAsientoRepository asientoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarBase() {
        jdbcTemplate.execute("delete from bloqueos_job");
        jdbcTemplate.execute("delete from tickets");
        jdbcTemplate.execute("delete from reservas");
        jdbcTemplate.execute("delete from disponibilidad_asientos");
        jdbcTemplate.execute("delete from disponibilidad_sectores");
        jdbcTemplate.execute("delete from disponibilidad_eventos");
    }

    @Test
    void liberaElAsientoYRestituyeElContadorUnaSolaVez() {
        Instant ahora = Instant.now();
        Contexto contexto = crearContextoNumerado(9101L, ahora.minus(Duration.ofMinutes(5)), false);

        int liberadas = reservaVencidaService.liberarVencidas(ahora);
        assertThat(liberadas).isEqualTo(1);

        assertThat(ticketRepository.findById(contexto.ticket().getId()).orElseThrow()
                .getEstadoTicket()).isEqualTo(EstadoTicket.EXPIRADO);
        assertThat(asientoRepository.findById(contexto.asiento().getId()).orElseThrow()
                .getEstado()).isEqualTo(EstadoDisponibilidad.LIBRE);
        assertThat(sectorRepository.findById(contexto.sector().getId()).orElseThrow()
                .getDisponibles()).isEqualTo(contexto.sector().getCapacidadTotal());
        assertThat(ticketRepository.findById(contexto.ticket().getId()).orElseThrow()
                .getAsiento()).isNull();

        assertThat(reservaVencidaService.liberarVencidas(ahora)).isZero();
        assertThat(sectorRepository.findById(contexto.sector().getId()).orElseThrow()
                .getDisponibles()).isEqualTo(contexto.sector().getCapacidadTotal());
    }

    @Test
    void noLiberaUnaReservaLuegoDeIniciarElPago() {
        Instant ahora = Instant.now();
        Contexto contexto = crearContextoNumerado(9102L, ahora.plus(Duration.ofMinutes(10)), false);

        reservaService.iniciarPago(contexto.reserva().getId(), contexto.reserva().getClienteId());

        jdbcTemplate.update("update reservas set expira_el = ? where id = ?",
                ahora.minusSeconds(60), contexto.reserva().getId());
        jdbcTemplate.update("update tickets set expira_el = ? where id = ?",
                ahora.minusSeconds(60), contexto.ticket().getId());

        assertThat(reservaVencidaService.liberarVencidas(ahora)).isZero();
        assertThat(ticketRepository.findById(contexto.ticket().getId()).orElseThrow()
                .getEstadoTicket()).isEqualTo(EstadoTicket.RESERVADO);
        assertThat(asientoRepository.findById(contexto.asiento().getId()).orElseThrow()
                .getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(sectorRepository.findById(contexto.sector().getId()).orElseThrow()
                .getDisponibles()).isZero();

        reservaService.confirmarPago(contexto.reserva().getId());

        assertThat(ticketRepository.findById(contexto.ticket().getId()).orElseThrow()
                .getEstadoTicket()).isEqualTo(EstadoTicket.EMITIDO);
        assertThat(asientoRepository.findById(contexto.asiento().getId()).orElseThrow()
                .getEstado()).isEqualTo(EstadoDisponibilidad.VENDIDO);
        assertThat(reservaVencidaService.liberarVencidas(ahora)).isZero();
    }

    @Test
    void alRechazarElPagoSeLiberaLaReservaVencida() {
        Instant ahora = Instant.now();
        Contexto contexto = crearContextoNumerado(9103L, ahora.plus(Duration.ofMinutes(10)), false);

        reservaService.iniciarPago(contexto.reserva().getId(), contexto.reserva().getClienteId());
        jdbcTemplate.update("update reservas set expira_el = ? where id = ?",
                ahora.minusSeconds(60), contexto.reserva().getId());
        jdbcTemplate.update("update tickets set expira_el = ? where id = ?",
                ahora.minusSeconds(60), contexto.ticket().getId());

        reservaService.rechazarPago(contexto.reserva().getId());

        assertThat(reservaVencidaService.liberarVencidas(ahora)).isEqualTo(1);
        assertThat(ticketRepository.findById(contexto.ticket().getId()).orElseThrow()
                .getEstadoTicket()).isEqualTo(EstadoTicket.EXPIRADO);
        assertThat(sectorRepository.findById(contexto.sector().getId()).orElseThrow()
                .getDisponibles()).isEqualTo(contexto.sector().getCapacidadTotal());
    }

    @Test
    void noPermitePagarUnaReservaVencida() {
        Instant ahora = Instant.now();
        Contexto contexto = crearContextoNumerado(9104L, ahora.minus(Duration.ofMinutes(1)), false);

        assertThatThrownBy(() -> reservaService.iniciarPago(
                contexto.reserva().getId(), contexto.reserva().getClienteId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("vencio");

        assertThat(reservaRepository.findById(contexto.reserva().getId()).orElseThrow()
                .isPagoEnCurso()).isFalse();
    }

    @Test
    void elBloqueoDistribuidoNoSePuedeAdquirirDosVeces() {
        Instant ahora = Instant.now();
        String nombre = "job-test-" + UUID.randomUUID();

        Optional<BloqueoDistribuidoService.Bloqueo> primero =
                bloqueoDistribuidoService.adquirir(nombre, Duration.ofMinutes(1));
        Optional<BloqueoDistribuidoService.Bloqueo> segundo =
                bloqueoDistribuidoService.adquirir(nombre, Duration.ofMinutes(1));

        assertThat(primero).isPresent();
        assertThat(segundo).isEmpty();

        bloqueoDistribuidoService.liberar(primero.orElseThrow());

        Optional<BloqueoDistribuidoService.Bloqueo> tercero =
                bloqueoDistribuidoService.adquirir(nombre, Duration.ofMinutes(1));
        assertThat(tercero).isPresent();

        bloqueoDistribuidoService.liberar(tercero.orElseThrow());
    }

    private Contexto crearContextoNumerado(Long eventoId, Instant expiraEl, boolean pagoEnCurso) {
        DisponibilidadEvento evento = eventoRepository.save(DisponibilidadEvento.builder()
                .eventoId(eventoId)
                .creadoEl(Instant.now())
                .limiteEntradasPorCliente(10)
                .build());

        DisponibilidadSector sector = sectorRepository.save(DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(1L)
                .tipo(TipoSector.NUMERADO)
                .capacidadTotal(1)
                .disponibles(0)
                .build());

        DisponibilidadAsiento asiento = asientoRepository.save(DisponibilidadAsiento.builder()
                .evento(evento)
                .sector(sector)
                .fila(1)
                .numero(1)
                .estado(EstadoDisponibilidad.RESERVADO)
                .build());

        Reserva reserva = reservaRepository.save(Reserva.builder()
                .eventoId(eventoId)
                .clienteId(42L)
                .sectorIdExterno(1L)
                .expiraEl(expiraEl)
                .creadoEl(Instant.now())
                .pagoEnCurso(pagoEnCurso)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .codigo_qr(UUID.randomUUID().toString())
                .estadoTicket(EstadoTicket.RESERVADO)
                .clienteId(42L)
                .expiraEl(expiraEl)
                .pagoEnCurso(pagoEnCurso)
                .evento(evento)
                .sector(sector)
                .asiento(asiento)
                .reserva(reserva)
                .build());

        return new Contexto(reserva, ticket, sector, asiento);
    }

    private record Contexto(Reserva reserva, Ticket ticket,
                            DisponibilidadSector sector, DisponibilidadAsiento asiento) {
    }
}
