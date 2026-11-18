package org.tudai.ticketya.devoluciones.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.devoluciones.model.RechazarDevolucionRequest;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.OperationLogger;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.devoluciones.client.EntradaReintegroDTO;
import org.tudai.ticketya.devoluciones.client.EventoClient;
import org.tudai.ticketya.devoluciones.client.TicketClient;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.domain.EstadoReintegroMasivo;
import org.tudai.ticketya.devoluciones.domain.ReintegroMasivo;
import org.tudai.ticketya.devoluciones.repository.DevolucionRepository;
import org.tudai.ticketya.devoluciones.repository.ReintegroMasivoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Log de operaciones críticas de devoluciones (TKY-35): cada entrada del reintegro masivo registra
 * REFUND OK o FAILED, y el rechazo de una devolución registra REFUND FAILED con el motivo.
 */
@ExtendWith(MockitoExtension.class)
class RegistroOperacionesReintegroTest {

    private static final Long EVENTO_ID = 500L;

    @Mock
    private ReintegroMasivoRepository procesoRepository;

    @Mock
    private DevolucionRepository devolucionRepository;

    @Mock
    private TicketClient ticketClient;

    @Mock
    private EventoClient eventoClient;

    @Mock
    private TransaccionesApiClient transaccionesClient;

    @Mock
    private NotificacionReintegroPublisher notificacionPublisher;

    @Mock
    private UserContext userContext;

    private ListAppender<ILoggingEvent> operaciones;

    @BeforeEach
    void setUp() {
        operaciones = new ListAppender<>();
        operaciones.start();
        ((Logger) LoggerFactory.getLogger(OperationLogger.LOGGER_NAME)).addAppender(operaciones);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(OperationLogger.LOGGER_NAME)).detachAppender(operaciones);
    }

    @Test
    void reintegroMasivo_cadaEntradaAcreditada_registraRefundOk() {
        prepararLote(entrada(101L, 10L), entrada(102L, 20L));
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenReturn(credito(EstadoTransaccion.COMPLETA, 9000L), credito(EstadoTransaccion.COMPLETA, 9001L));
        when(devolucionRepository.findClientesReintegrados(EVENTO_ID, ReintegroMasivoService.ORIGEN))
                .thenReturn(List.of(10L, 20L));

        reintegroMasivo().procesarPendientes();

        assertThat(lineas()).containsExactly(
                "REFUND OK user=10 Ticket=101",
                "REFUND OK user=20 Ticket=102");
    }

    @Test
    void reintegroMasivo_creditoFallido_registraRefundFailedConElError() {
        prepararLote(entrada(101L, 10L));
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenReturn(credito(EstadoTransaccion.FALLIDA, null));

        reintegroMasivo().procesarPendientes();

        assertThat(lineas()).containsExactly(
                "REFUND FAILED user=10 Ticket=101 reason=No se pudo acreditar la entrada 101: FALLIDA");
    }

    @Test
    void reintegroMasivo_entradaYaAcreditada_noRegistraDeNuevo() {
        prepararLote(entrada(101L, 10L));
        when(devolucionRepository.existsByTicketIdAndOrigen(101L, ReintegroMasivoService.ORIGEN)).thenReturn(true);
        when(devolucionRepository.findClientesReintegrados(EVENTO_ID, ReintegroMasivoService.ORIGEN))
                .thenReturn(List.of(10L));

        reintegroMasivo().procesarPendientes();

        assertThat(lineas()).isEmpty();
    }

    @Test
    void rechazoDeUnaDevolucion_registraRefundFailedConElMotivo() {
        when(userContext.isPresent()).thenReturn(true);
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(999L);
        when(devolucionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Devolucion.builder()
                .id(1L).clienteId(10L).ticketId(100L).estado(EstadoDevolucion.PENDIENTE).motivo("No puedo ir").build()));
        when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenAnswer(i -> i.getArgument(0));
        RechazarDevolucionRequest request = new RechazarDevolucionRequest();
        request.setMotivo("Fuera de plazo");

        new DevolucionService(devolucionRepository, ticketClient, eventoClient, userContext)
                .rechazarDevolucion(1L, request);

        assertThat(lineas()).containsExactly("REFUND FAILED user=10 Ticket=100 reason=rechazada: Fuera de plazo");
        assertThat(operaciones.list.get(0).getMDCPropertyMap())
                .containsEntry("operation", "REFUND").containsEntry("result", "FAILED");
    }

    private ReintegroMasivoService reintegroMasivo() {
        ReintegroMasivoService service = new ReintegroMasivoService(procesoRepository, devolucionRepository,
                ticketClient, transaccionesClient, notificacionPublisher);
        service.setBatchSize(100);
        service.setMaxIntentos(3);
        service.setTimeoutSegundos(120);
        return service;
    }

    private void prepararLote(EntradaReintegroDTO... entradas) {
        ReintegroMasivo proceso = ReintegroMasivo.builder()
                .id(1L).eventoId(EVENTO_ID).estado(EstadoReintegroMasivo.PENDIENTE).build();
        when(procesoRepository.findByEstadoOrderByIdAsc(eq(EstadoReintegroMasivo.PENDIENTE), any(Pageable.class)))
                .thenReturn(List.of(proceso));
        when(procesoRepository.reclamar(eq(1L), eq(EstadoReintegroMasivo.EN_PROCESO),
                eq(EstadoReintegroMasivo.PENDIENTE), any(Instant.class))).thenReturn(1);
        when(ticketClient.listarEntradasVendidas(EVENTO_ID, 0L, 100)).thenReturn(List.of(entradas));
        org.mockito.Mockito.lenient()
                .when(devolucionRepository.existsByTicketIdAndOrigen(anyLong(), eq(ReintegroMasivoService.ORIGEN)))
                .thenReturn(false);
    }

    private EntradaReintegroDTO entrada(Long ticketId, Long clienteId) {
        return EntradaReintegroDTO.builder()
                .id(ticketId).clienteId(clienteId).precio(1500.0).estado("VENDIDO")
                .organizadorId(99L).transaccionId(700L + ticketId).build();
    }

    private TransaccionResponse credito(EstadoTransaccion estado, Long id) {
        TransaccionResponse response = new TransaccionResponse();
        response.setId(id);
        response.setEstado(estado);
        return response;
    }

    private List<String> lineas() {
        return operaciones.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
