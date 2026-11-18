package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.domain.TipoSector;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-compradores;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "transacciones.name=transacciones"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TicketRepositoryCompradoresTest {

    private static final List<EstadoTicket> ESTADOS_COMPRADOR = List.of(
            EstadoTicket.EMITIDO,
            EstadoTicket.VENDIDO,
            EstadoTicket.USADO,
            EstadoTicket.DEVUELTO);

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findClienteIdsDistintosPorEvento_incluyeSoloComprasDistintasYOrdenadas() {
        DisponibilidadEvento evento = crearEvento(7L);
        DisponibilidadSector sector = crearSector(evento, 100L);
        DisponibilidadEvento otroEvento = crearEvento(8L);
        DisponibilidadSector otroSector = crearSector(otroEvento, 200L);

        crearTicket("qr-1", 30L, EstadoTicket.EMITIDO, evento, sector);
        crearTicket("qr-2", 30L, EstadoTicket.USADO, evento, sector);
        crearTicket("qr-3", 10L, EstadoTicket.USADO, evento, sector);
        crearTicket("qr-4", 20L, EstadoTicket.DEVUELTO, evento, sector);
        crearTicket("qr-5", 40L, EstadoTicket.RESERVADO, evento, sector);
        crearTicket("qr-6", 50L, EstadoTicket.CANCELADO, evento, sector);
        crearTicket("qr-7", 60L, EstadoTicket.EXPIRADO, evento, sector);
        crearTicket("qr-8", 70L, EstadoTicket.LIBERADO, evento, sector);
        crearTicket("qr-9", null, EstadoTicket.EMITIDO, evento, sector);
        crearTicket("qr-10", 80L, EstadoTicket.EMITIDO, otroEvento, otroSector);
        crearTicket("qr-vendido", 45L, EstadoTicket.VENDIDO, evento, sector);
        crearTicket("qr-vendido-otro", 85L, EstadoTicket.VENDIDO, otroEvento, otroSector);

        entityManager.flush();

        assertThat(ticketRepository.findClienteIdsDistintosPorEvento(7L, ESTADOS_COMPRADOR))
                .containsExactly(10L, 20L, 30L, 45L);
        assertThat(ticketRepository.findClienteIdsDistintosPorEvento(8L, ESTADOS_COMPRADOR))
                .containsExactly(80L, 85L);
    }

    @Test
    void findClienteIdsDistintosPorEvento_cuandoNoHayCompras_devuelveListaVacia() {
        DisponibilidadEvento evento = crearEvento(9L);
        DisponibilidadSector sector = crearSector(evento, 300L);
        crearTicket("qr-11", 90L, EstadoTicket.RESERVADO, evento, sector);
        entityManager.flush();

        assertThat(ticketRepository.findClienteIdsDistintosPorEvento(9L, ESTADOS_COMPRADOR))
                .isEmpty();
    }

    private DisponibilidadEvento crearEvento(Long eventoId) {
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(eventoId)
                .creadoEl(Instant.now())
                .limiteEntradasPorCliente(4)
                .build();
        entityManager.persist(evento);
        return evento;
    }

    private DisponibilidadSector crearSector(DisponibilidadEvento evento, Long sectorIdExterno) {
        DisponibilidadSector sector = DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(sectorIdExterno)
                .tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(100)
                .disponibles(100)
                .build();
        entityManager.persist(sector);
        return sector;
    }

    private void crearTicket(String codigoQr, Long clienteId, EstadoTicket estado,
                             DisponibilidadEvento evento, DisponibilidadSector sector) {
        entityManager.persist(Ticket.builder()
                .codigo_qr(codigoQr)
                .estadoTicket(estado)
                .clienteId(clienteId)
                .evento(evento)
                .sector(sector)
                .build());
    }
}
