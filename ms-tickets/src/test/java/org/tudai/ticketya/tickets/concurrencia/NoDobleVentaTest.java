package org.tudai.ticketya.tickets.concurrencia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.dto.*;
import org.tudai.ticketya.tickets.service.DisponibilidadService;
import org.tudai.ticketya.tickets.service.PagoService;
import org.tudai.ticketya.tickets.service.ReservaService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas de no doble venta contra Postgres real (Testcontainers), con muchos hilos a la vez.
 * Necesita Docker corriendo.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.datasource.password=ignored",
        "spring.datasource.hikari.maximum-pool-size=20",
        "ticketya.logging.dir=target/test-logs"
})
@Testcontainers
class NoDobleVentaTest {

    private static final int HILOS = 100;

    static {
        // El driver manda la zona horaria de la JVM al conectarse, y Postgres 18 rechaza el alias
        // "America/Buenos_Aires". Las fechas del servicio son Instant, así que UTC no cambia nada.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @MockitoBean
    private TransaccionesApiClient transaccionesClient;

    @Autowired
    private DisponibilidadService disponibilidadService;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private JdbcTemplate jdbc;

    /** Simula ms-transacciones: idempotente por ticket, cuenta los cobros reales. */
    private final Map<Long, TransaccionResponse> cobrosPorTicket = new ConcurrentHashMap<>();
    private final AtomicInteger cobrosReales = new AtomicInteger();
    private final AtomicLong idsTransaccion = new AtomicLong(1000);

    @BeforeEach
    void simularTransacciones() {
        cobrosPorTicket.clear();
        cobrosReales.set(0);
        when(transaccionesClient.generarDebito(any())).thenAnswer(invocacion -> {
            GenerarTransaccionRequest request = invocacion.getArgument(0);
            return cobrosPorTicket.computeIfAbsent(request.getTicketId(), ticketId -> {
                cobrosReales.incrementAndGet();
                return new TransaccionResponse()
                        .id(idsTransaccion.incrementAndGet())
                        .ticketId(ticketId)
                        .estado(EstadoTransaccion.COMPLETA);
            });
        });
    }

    @Test
    void cienHilosReservandoElMismoAsiento_soloUnoLoConsigue() throws Exception {
        long eventoId = 9101L;
        publicar(eventoId, SectorDisponibilidadDTO.builder()
                .sectorId(1L).tipo(TipoSector.NUMERADO).filas(2).asientosPorFila(5).precio(1000.0).build());

        Resultado resultado = enParalelo(HILOS, i -> () -> reservaService.reservar(eventoId, 1000L + i,
                ReservaRequestDTO.builder()
                        .sectorId(1L)
                        .asientos(List.of(AsientoDTO.builder().fila(1).numero(1).build()))
                        .build()));

        assertThat(resultado.exitos()).isEqualTo(1);
        assertThat(resultado.conflictos()).isEqualTo(HILOS - 1);
        assertThat(resultado.otrosErrores()).isEmpty();
        assertThat(jdbc.queryForObject("""
                select count(*) from tickets t
                join disponibilidad_asientos a on a.id = t.asiento_id
                where a.evento_id = ? and a.fila = 1 and a.numero = 1
                """, Long.class, eventoId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select disponibles from disponibilidad_sectores where evento_id = ?", Integer.class, eventoId))
                .isEqualTo(9);
    }

    @Test
    void cienHilosPorElUltimoLugarDeLaZona_soloUnoLoConsigueYElContadorQuedaEnCero() throws Exception {
        long eventoId = 9102L;
        publicar(eventoId, SectorDisponibilidadDTO.builder()
                .sectorId(1L).tipo(TipoSector.NO_NUMERADO).capacidadMaxima(5).precio(1000.0).build());
        reservaService.reservar(eventoId, 1L, ReservaRequestDTO.builder().sectorId(1L).cantidad(4).build());

        Resultado resultado = enParalelo(HILOS, i -> () -> reservaService.reservar(eventoId, 1000L + i,
                ReservaRequestDTO.builder().sectorId(1L).cantidad(1).build()));

        assertThat(resultado.exitos()).isEqualTo(1);
        assertThat(resultado.conflictos()).isEqualTo(HILOS - 1);
        assertThat(resultado.otrosErrores()).isEmpty();
        assertThat(jdbc.queryForObject(
                "select disponibles from disponibilidad_sectores where evento_id = ?", Integer.class, eventoId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from tickets where evento_id = ?", Long.class, eventoId))
                .isEqualTo(5L);
    }

    @Test
    void elMismoPagoReintentadoNVeces_generaUnSoloCobroYUnSoloConjuntoDeTickets() throws Exception {
        long eventoId = 9103L;
        publicar(eventoId, SectorDisponibilidadDTO.builder()
                .sectorId(1L).tipo(TipoSector.NO_NUMERADO).capacidadMaxima(50).precio(1500.0).build());
        ReservaResponseDTO reserva = reservaService.reservar(eventoId, 42L,
                ReservaRequestDTO.builder().sectorId(1L).cantidad(2).build());
        Long reservaId = reserva.getIdReserva();
        PagoRequestDTO pago = PagoRequestDTO.builder().medioPago("TARJETA").build();

        List<CompraResponseDTO> compras = new CopyOnWriteArrayList<>();
        Resultado concurrentes = enParalelo(20, i -> () ->
                compras.add(pagoService.pagar(reservaId, 42L, "misma-clave", pago)));
        for (int i = 0; i < 10; i++) {
            compras.add(pagoService.pagar(reservaId, 42L, "misma-clave", pago));
        }

        assertThat(concurrentes.otrosErrores()).isEmpty();
        assertThat(concurrentes.conflictos()).isZero();
        assertThat(compras).hasSize(30);
        assertThat(compras).extracting(CompraResponseDTO::getVentaId).containsOnly(compras.get(0).getVentaId());

        assertThat(cobrosReales.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from ventas where reserva_id = ?", Long.class, reservaId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from venta_items vi join ventas v on v.id = vi.venta_id where v.reserva_id = ?",
                Long.class, reservaId)).isEqualTo(2L);
        assertThat(jdbc.queryForList(
                "select estado_ticket from tickets where reserva_id = ?", String.class, reservaId))
                .containsExactly("VENDIDO", "VENDIDO");
    }

    private void publicar(long eventoId, SectorDisponibilidadDTO sector) {
        disponibilidadService.publicar(PublicarEventoRequestDTO.builder()
                .eventoId(eventoId)
                .organizadorId(5L)
                .limiteEntradasPorCliente(4)
                .sectores(List.of(sector))
                .build());
    }

    private record Resultado(int exitos, int conflictos, List<Throwable> otrosErrores) {}

    /** Lanza {@code cantidad} tareas que arrancan a la vez y cuenta éxitos y conflictos (409). */
    private Resultado enParalelo(int cantidad, IntFunction<Runnable> tarea) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(cantidad);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Runnable runnable = tarea.apply(i);
            futuros.add(pool.submit(() -> {
                largada.await();
                runnable.run();
                return null;
            }));
        }
        largada.countDown();

        int exitos = 0;
        int conflictos = 0;
        List<Throwable> otros = new ArrayList<>();
        for (Future<?> futuro : futuros) {
            try {
                futuro.get(2, TimeUnit.MINUTES);
                exitos++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ConflictException) {
                    conflictos++;
                } else {
                    otros.add(e.getCause());
                }
            }
        }
        pool.shutdownNow();
        return new Resultado(exitos, conflictos, otros);
    }
}
