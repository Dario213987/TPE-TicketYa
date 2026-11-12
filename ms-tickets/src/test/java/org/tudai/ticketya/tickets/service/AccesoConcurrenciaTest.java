package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-acceso-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "transacciones.name=transacciones"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccesoConcurrenciaTest {

    private static final Long EVENTO_ID = 9101L;
    private static final String CODIGO_QR = "qr-acceso-concurrente";

    @Autowired
    private AccesoService accesoService;

    @Autowired
    private DisponibilidadEventoRepository eventoRepository;

    @Autowired
    private DisponibilidadSectorRepository sectorRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void escaneosSimultaneosDelMismoQr_soloUnoValidaElAcceso() throws Exception {
        DisponibilidadEvento evento = eventoRepository.save(DisponibilidadEvento.builder()
                .eventoId(EVENTO_ID)
                .creadoEl(Instant.now())
                .limiteEntradasPorCliente(10)
                .build());
        DisponibilidadSector sector = sectorRepository.save(DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(1L)
                .tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(10)
                .disponibles(10)
                .build());
        ticketRepository.save(Ticket.builder()
                .codigo_qr(CODIGO_QR)
                .estadoTicket(EstadoTicket.VENDIDO)
                .evento(evento)
                .sector(sector)
                .build());

        int escaneos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(escaneos);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        for (int i = 0; i < escaneos; i++) {
            resultados.add(pool.submit(() -> {
                inicio.await();
                try {
                    accesoService.validar(EVENTO_ID, CODIGO_QR);
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            }));
        }

        inicio.countDown();
        int exitos = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get(30, TimeUnit.SECONDS)) {
                exitos++;
            }
        }
        pool.shutdownNow();

        assertThat(exitos).isEqualTo(1);
        assertThat(ticketRepository.buscarPorCodigoQr(CODIGO_QR).orElseThrow().getEstadoTicket())
                .isEqualTo(EstadoTicket.USADO);
    }
}
