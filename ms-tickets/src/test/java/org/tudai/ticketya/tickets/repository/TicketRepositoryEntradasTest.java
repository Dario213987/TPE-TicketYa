package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.tudai.ticketya.tickets.domain.*;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-entradas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "transacciones.name=transacciones"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TicketRepositoryEntradasTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void findByClienteIdAndEstadoTicketIn_soloTraeLasEntradasDelClientePaginadas() {
        DisponibilidadEvento evento = em.persist(DisponibilidadEvento.builder()
                .eventoId(7L).creadoEl(Instant.now()).build());
        DisponibilidadSector sector = em.persist(DisponibilidadSector.builder()
                .evento(evento).sectorIdExterno(3L).tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(10).disponibles(5).build());
        Instant base = Instant.parse("2026-11-01T00:00:00Z");
        persistir(evento, sector, "a", 42L, EstadoTicket.VENDIDO, base);
        persistir(evento, sector, "b", 42L, EstadoTicket.USADO, base.plusSeconds(60));
        persistir(evento, sector, "c", 42L, EstadoTicket.RESERVADO, base.plusSeconds(120));
        persistir(evento, sector, "d", 99L, EstadoTicket.VENDIDO, base.plusSeconds(180));
        em.flush();
        em.clear();

        Page<Ticket> pagina = ticketRepository.findByClienteIdAndEstadoTicketIn(42L,
                EnumSet.of(EstadoTicket.VENDIDO, EstadoTicket.USADO),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "fechaEmision")));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(Ticket::getCodigo_qr).containsExactly("b", "a");
        assertThat(pagina.getContent().get(0).getSector().getSectorIdExterno()).isEqualTo(3L);
    }

    private void persistir(DisponibilidadEvento evento, DisponibilidadSector sector, String qr, Long clienteId,
                           EstadoTicket estado, Instant emision) {
        em.persist(Ticket.builder()
                .codigo_qr(qr).estadoTicket(estado).fechaEmision(emision).clienteId(clienteId)
                .evento(evento).sector(sector).build());
    }
}
