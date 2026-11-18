package org.tudai.ticketya.devoluciones.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.devoluciones.client.EntradaReintegroDTO;
import org.tudai.ticketya.devoluciones.client.TicketClient;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.domain.EstadoReintegroMasivo;
import org.tudai.ticketya.devoluciones.domain.ReintegroMasivo;
import org.tudai.ticketya.devoluciones.repository.DevolucionRepository;
import org.tudai.ticketya.devoluciones.repository.ReintegroMasivoRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReintegroMasivoServiceTest {

    private static final Long EVENTO_ID = 500L;

    @Mock
    private ReintegroMasivoRepository procesoRepository;

    @Mock
    private DevolucionRepository devolucionRepository;

    @Mock
    private TicketClient ticketClient;

    @Mock
    private TransaccionesApiClient transaccionesClient;

    @Mock
    private NotificacionReintegroPublisher notificacionPublisher;

    @InjectMocks
    private ReintegroMasivoService service;

    @BeforeEach
    void setUp() {
        service.setBatchSize(100);
        service.setMaxIntentos(3);
        service.setTimeoutSegundos(120);
    }

    private ReintegroMasivo proceso() {
        return ReintegroMasivo.builder()
                .id(1L)
                .eventoId(EVENTO_ID)
                .estado(EstadoReintegroMasivo.PENDIENTE)
                .build();
    }

    private EntradaReintegroDTO entrada(Long ticketId, Long clienteId, Double precio) {
        return EntradaReintegroDTO.builder()
                .id(ticketId)
                .clienteId(clienteId)
                .precio(precio)
                .estado("VENDIDO")
                .organizadorId(99L)
                .transaccionId(700L + ticketId)
                .build();
    }

    private void prepararClaim(ReintegroMasivo proceso) {
        when(procesoRepository.findByEstadoOrderByIdAsc(eq(EstadoReintegroMasivo.PENDIENTE), any(Pageable.class)))
                .thenReturn(List.of(proceso));
        when(procesoRepository.reclamar(eq(proceso.getId()), eq(EstadoReintegroMasivo.EN_PROCESO),
                eq(EstadoReintegroMasivo.PENDIENTE), any(Instant.class))).thenReturn(1);
    }

    private TransaccionResponse credito(Long id) {
        TransaccionResponse response = new TransaccionResponse();
        response.setId(id);
        response.setEstado(EstadoTransaccion.COMPLETA);
        return response;
    }

    @Test
    @DisplayName("iniciar dos veces para el mismo evento no crea dos procesos")
    void iniciar_duplicado_noCreaOtroProceso() {
        when(procesoRepository.existsByEventoId(EVENTO_ID)).thenReturn(false, true);

        service.iniciar(EVENTO_ID);
        service.iniciar(EVENTO_ID);

        verify(procesoRepository, times(1)).saveAndFlush(any(ReintegroMasivo.class));
    }

    @Test
    @DisplayName("El proceso acredita cada entrada, libera reservas y notifica a cada comprador")
    void procesarPendientes_reintegroCompleto_acreditaYNotifica() {
        ReintegroMasivo proceso = proceso();
        prepararClaim(proceso);
        when(ticketClient.listarEntradasVendidas(EVENTO_ID, 0L, 100))
                .thenReturn(List.of(entrada(101L, 10L, 1500.0), entrada(102L, 20L, 2500.0)));
        when(devolucionRepository.existsByTicketIdAndOrigen(anyLong(), eq(ReintegroMasivoService.ORIGEN)))
                .thenReturn(false);
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenReturn(credito(9000L), credito(9001L));
        when(devolucionRepository.findClientesReintegrados(EVENTO_ID, ReintegroMasivoService.ORIGEN))
                .thenReturn(List.of(10L, 20L));

        service.procesarPendientes();

        verify(procesoRepository, atLeastOnce()).liberarBloqueosVencidos(
                eq(EstadoReintegroMasivo.EN_PROCESO), eq(EstadoReintegroMasivo.PENDIENTE), any(Instant.class));
        verify(ticketClient, times(1)).liberarReservas(EVENTO_ID);

        ArgumentCaptor<GenerarTransaccionRequest> credits = ArgumentCaptor.forClass(GenerarTransaccionRequest.class);
        verify(transaccionesClient, times(2)).generarCredito(credits.capture());
        assertThat(credits.getAllValues()).extracting(GenerarTransaccionRequest::getTicketId)
                .containsExactly(101L, 102L);
        assertThat(credits.getAllValues()).extracting(GenerarTransaccionRequest::getMonto)
                .containsExactly(1500.0, 2500.0);

        ArgumentCaptor<Devolucion> devoluciones = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository, times(2)).saveAndFlush(devoluciones.capture());
        assertThat(devoluciones.getAllValues()).allSatisfy(devolucion -> {
            assertThat(devolucion.getEstado()).isEqualTo(EstadoDevolucion.APROBADA);
            assertThat(devolucion.getOrigen()).isEqualTo(ReintegroMasivoService.ORIGEN);
            assertThat(devolucion.getEventoId()).isEqualTo(EVENTO_ID);
        });
        assertThat(devoluciones.getAllValues()).extracting(Devolucion::getTransaccionId)
                .containsExactly(9000L, 9001L);

        verify(ticketClient, times(1)).marcarDevuelto(101L);
        verify(ticketClient, times(1)).marcarDevuelto(102L);

        ArgumentCaptor<NotificacionEntranteDTO> mensajes = ArgumentCaptor.forClass(NotificacionEntranteDTO.class);
        verify(notificacionPublisher, times(2)).publicar(mensajes.capture());
        assertThat(mensajes.getAllValues()).extracting(NotificacionEntranteDTO::getMotivo)
                .containsExactly(MotivoNotificacion.CANCELACION, MotivoNotificacion.CANCELACION);
        assertThat(mensajes.getAllValues()).extracting(NotificacionEntranteDTO::getDestinatarioId)
                .containsExactly(10L, 20L);
        assertThat(mensajes.getAllValues()).extracting(NotificacionEntranteDTO::getMensajeId)
                .containsExactly("reintegro-masivo-500-10", "reintegro-masivo-500-20");

        assertThat(proceso.getEstado()).isEqualTo(EstadoReintegroMasivo.COMPLETADO);
        assertThat(proceso.isNotificado()).isTrue();
        assertThat(proceso.getProcesados()).isEqualTo(2);
        verify(procesoRepository, atLeastOnce()).save(proceso);
    }

    @Test
    @DisplayName("El proceso se hace por lotes y se reanuda desde el último ticket")
    void procesarPendientes_porLotes_reanudaDesdeElCursor() {
        service.setBatchSize(1);
        ReintegroMasivo proceso = proceso();
        prepararClaim(proceso);
        when(ticketClient.listarEntradasVendidas(EVENTO_ID, 0L, 1))
                .thenReturn(List.of(entrada(101L, 10L, 1500.0)));
        when(ticketClient.listarEntradasVendidas(EVENTO_ID, 101L, 1)).thenReturn(List.of());
        when(devolucionRepository.existsByTicketIdAndOrigen(anyLong(), eq(ReintegroMasivoService.ORIGEN)))
                .thenReturn(false);
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class))).thenReturn(credito(9000L));

        service.procesarPendientes();

        assertThat(proceso.getEstado()).isEqualTo(EstadoReintegroMasivo.PENDIENTE);
        assertThat(proceso.getUltimoTicketId()).isEqualTo(101L);
        verify(notificacionPublisher, never()).publicar(any());

        when(procesoRepository.reclamar(eq(proceso.getId()), eq(EstadoReintegroMasivo.EN_PROCESO),
                eq(EstadoReintegroMasivo.PENDIENTE), any(Instant.class))).thenReturn(1);
        when(devolucionRepository.findClientesReintegrados(EVENTO_ID, ReintegroMasivoService.ORIGEN))
                .thenReturn(List.of(10L));

        service.procesarPendientes();

        assertThat(proceso.getEstado()).isEqualTo(EstadoReintegroMasivo.COMPLETADO);
        verify(transaccionesClient, times(1)).generarCredito(any(GenerarTransaccionRequest.class));
        verify(notificacionPublisher, times(1)).publicar(any(NotificacionEntranteDTO.class));
    }

    @Test
    @DisplayName("Un ticket ya reintegrado no recibe un segundo crédito")
    void procesarPendientes_ticketYaReintegrado_noGeneraOtroCredito() {
        ReintegroMasivo proceso = proceso();
        prepararClaim(proceso);
        when(ticketClient.listarEntradasVendidas(EVENTO_ID, 0L, 100))
                .thenReturn(List.of(entrada(101L, 10L, 1500.0)));
        when(devolucionRepository.existsByTicketIdAndOrigen(101L, ReintegroMasivoService.ORIGEN))
                .thenReturn(true);
        when(devolucionRepository.findClientesReintegrados(EVENTO_ID, ReintegroMasivoService.ORIGEN))
                .thenReturn(List.of(10L));

        service.procesarPendientes();

        verify(transaccionesClient, never()).generarCredito(any(GenerarTransaccionRequest.class));
        verify(ticketClient, times(1)).marcarDevuelto(101L);
        assertThat(proceso.getProcesados()).isZero();
        assertThat(proceso.getEstado()).isEqualTo(EstadoReintegroMasivo.COMPLETADO);
        verify(notificacionPublisher, times(1)).publicar(any(NotificacionEntranteDTO.class));
    }

    @Test
    @DisplayName("Si falla una acreditación el lote queda pendiente y se reintenta desde el mismo ticket")
    void procesarPendientes_creditoFallido_quedaPendienteParaReintentar() {
        ReintegroMasivo proceso = proceso();
        prepararClaim(proceso);
        when(ticketClient.listarEntradasVendidas(EVENTO_ID, 0L, 100))
                .thenReturn(List.of(entrada(101L, 10L, 1500.0)));
        when(devolucionRepository.existsByTicketIdAndOrigen(anyLong(), eq(ReintegroMasivoService.ORIGEN)))
                .thenReturn(false);
        TransaccionResponse fallida = new TransaccionResponse();
        fallida.setEstado(EstadoTransaccion.FALLIDA);
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class))).thenReturn(fallida);

        service.procesarPendientes();

        assertThat(proceso.getEstado()).isEqualTo(EstadoReintegroMasivo.PENDIENTE);
        assertThat(proceso.getUltimoTicketId()).isNull();
        assertThat(proceso.getIntentos()).isEqualTo(1);
        verify(devolucionRepository, never()).saveAndFlush(any(Devolucion.class));
        verify(notificacionPublisher, never()).publicar(any());
    }

    @Test
    @DisplayName("Si nadie reclama el proceso, otro lote no lo ejecuta")
    void procesarPendientes_otroWorkerGanaElClaim_noProcesa() {
        ReintegroMasivo proceso = proceso();
        when(procesoRepository.findByEstadoOrderByIdAsc(eq(EstadoReintegroMasivo.PENDIENTE), any(Pageable.class)))
                .thenReturn(List.of(proceso));
        when(procesoRepository.reclamar(eq(proceso.getId()), eq(EstadoReintegroMasivo.EN_PROCESO),
                eq(EstadoReintegroMasivo.PENDIENTE), any(Instant.class))).thenReturn(0);

        service.procesarPendientes();

        verify(ticketClient, never()).liberarReservas(anyLong());
        verify(transaccionesClient, never()).generarCredito(any(GenerarTransaccionRequest.class));
    }
}
