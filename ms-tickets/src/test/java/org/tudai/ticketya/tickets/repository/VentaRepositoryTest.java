package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.tudai.ticketya.tickets.domain.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda una venta con sus ítems sobre el esquema de Liquibase, para verificar el mapeo
 * (cascada de ítems y referencias cruzadas con tickets).
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-ventas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "transacciones.name=transacciones"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VentaRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    void guardaLaVentaConSusItemsYLosTicketsLaReferencian() {
        Instant ahora = Instant.now();
        DisponibilidadEvento evento = em.persist(DisponibilidadEvento.builder()
                .eventoId(7L).creadoEl(ahora).organizadorId(5L).build());
        DisponibilidadSector sector = em.persist(DisponibilidadSector.builder()
                .evento(evento).sectorIdExterno(3L).tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(10).disponibles(9).precio(1500.0).build());
        Reserva reserva = em.persist(Reserva.builder()
                .eventoId(7L).clienteId(42L).sectorIdExterno(3L).expiraEl(ahora).creadoEl(ahora)
                .estadoPago(EstadoPago.APROBADO).medioPago("TARJETA").build());
        Ticket ticket = em.persist(Ticket.builder()
                .codigo_qr("qr-1").estadoTicket(EstadoTicket.VENDIDO).precio(1500.0)
                .fechaEmision(ahora).clienteId(42L).evento(evento).sector(sector).reserva(reserva).build());

        Venta venta = Venta.builder()
                .reservaId(reserva.getId()).clienteId(42L).eventoId(7L)
                .total(1500.0).medioPago("TARJETA").creadoEl(ahora).build();
        venta.agregarItem(VentaItem.builder()
                .ticket(ticket).sectorIdExterno(3L).precio(1500.0).transaccionId(501L).build());
        Venta guardada = ventaRepository.save(venta);
        ticket.setVenta(guardada);
        em.flush();
        em.clear();

        Venta leida = ventaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTicket().getId()).isEqualTo(ticket.getId());
            assertThat(item.getPrecio()).isEqualTo(1500.0);
            assertThat(item.getTransaccionId()).isEqualTo(501L);
        });
        assertThat(em.find(Ticket.class, ticket.getId()).getVenta().getId()).isEqualTo(guardada.getId());
    }
}
