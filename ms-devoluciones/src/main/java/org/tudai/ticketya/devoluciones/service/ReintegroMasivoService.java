package org.tudai.ticketya.devoluciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.Operation;
import org.tudai.ticketya.core.OperationLogger;
import org.tudai.ticketya.devoluciones.client.EntradaReintegroDTO;
import org.tudai.ticketya.devoluciones.client.TicketClient;
import org.tudai.ticketya.devoluciones.client.TransaccionesHeadersInterceptor;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.domain.EstadoReintegroMasivo;
import org.tudai.ticketya.devoluciones.domain.ReintegroMasivo;
import org.tudai.ticketya.devoluciones.repository.DevolucionRepository;
import org.tudai.ticketya.devoluciones.repository.ReintegroMasivoRepository;

import java.time.Instant;
import java.util.List;

/**
 * Reintegro automático de todas las entradas vendidas de un evento cancelado.
 * <p>
 * El alta del proceso es idempotente y no hace trabajo pesado: el job lo va ejecutando
 * por lotes con un cursor ({@code ultimoTicketId}). Cada entrada se acredita como máximo
 * una vez: primero se verifica el registro local y, además, ms-transacciones es idempotente
 * por (ticket, cliente, organizador, tipo). Al terminar se publica una notificación por
 * comprador con un mensajeId determinístico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReintegroMasivoService {

    /** Entidad del log de operaciones críticas (TKY-35): el reintegro se registra por entrada. */
    static final String ENTIDAD_REINTEGRO = "Ticket";

    static final String ORIGEN = "CANCELACION_EVENTO";
    private static final String MOTIVO = "Evento cancelado: reintegro automático";
    private static final long CURSOR_INICIAL = 0L;

    private final ReintegroMasivoRepository procesoRepository;
    private final DevolucionRepository devolucionRepository;
    private final TicketClient ticketClient;
    private final TransaccionesApiClient transaccionesClient;
    private final NotificacionReintegroPublisher notificacionPublisher;

    @Value("${ticketya.devoluciones.reintegro.batch-size:100}")
    private int batchSize = 100;

    @Value("${ticketya.devoluciones.reintegro.max-intentos:10}")
    private int maxIntentos = 10;

    @Value("${ticketya.devoluciones.reintegro.timeout-segundos:120}")
    private long timeoutSegundos = 120;

    /**
     * Registra el proceso cuando se cancela el evento. Llamarlo dos veces no crea otro
     * proceso ni reintegra dos veces las entradas.
     */
    @Transactional
    public void iniciar(Long eventoId) {
        if (procesoRepository.existsByEventoId(eventoId)) {
            return;
        }

        try {
            procesoRepository.saveAndFlush(ReintegroMasivo.builder()
                    .eventoId(eventoId)
                    .estado(EstadoReintegroMasivo.PENDIENTE)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("El reintegro masivo del evento {} ya estaba registrado", eventoId);
        }
    }

    /**
     * Procesa un lote por cada reintegro pendiente. Se llama desde el scheduler y también
     * puede invocarse manualmente para reanudar después de una interrupción.
     */
    public void procesarPendientes() {
        procesoRepository.liberarBloqueosVencidos(
                EstadoReintegroMasivo.EN_PROCESO,
                EstadoReintegroMasivo.PENDIENTE,
                Instant.now().minusSeconds(timeoutSegundos));

        List<ReintegroMasivo> pendientes = procesoRepository.findByEstadoOrderByIdAsc(
                EstadoReintegroMasivo.PENDIENTE, PageRequest.of(0, 10));

        for (ReintegroMasivo pendiente : pendientes) {
            if (procesoRepository.reclamar(pendiente.getId(),
                    EstadoReintegroMasivo.EN_PROCESO,
                    EstadoReintegroMasivo.PENDIENTE,
                    Instant.now()) == 0) {
                continue;
            }
            procesarLote(pendiente);
        }
    }

    private void procesarLote(ReintegroMasivo proceso) {
        try {
            if (!proceso.isReservasLiberadas()) {
                ticketClient.liberarReservas(proceso.getEventoId());
                proceso.setReservasLiberadas(true);
            }

            long desdeId = proceso.getUltimoTicketId() == null ? CURSOR_INICIAL : proceso.getUltimoTicketId();
            List<EntradaReintegroDTO> entradas = ticketClient.listarEntradasVendidas(
                    proceso.getEventoId(), desdeId, batchSize);

            for (EntradaReintegroDTO entrada : entradas) {
                if (devolucionRepository.existsByTicketIdAndOrigen(entrada.getId(), ORIGEN)) {
                    ticketClient.marcarDevuelto(entrada.getId());
                } else {
                    reintegrar(proceso.getEventoId(), entrada);
                    proceso.setProcesados(proceso.getProcesados() + 1);
                }
                proceso.setUltimoTicketId(entrada.getId());
            }

            proceso.setFallos(0);
            proceso.setIntentos(0);
            if (entradas.size() == batchSize) {
                proceso.setEstado(EstadoReintegroMasivo.PENDIENTE);
                procesoRepository.save(proceso);
                return;
            }

            notificarCompradores(proceso.getEventoId());
            proceso.setNotificado(true);
            proceso.setEstado(EstadoReintegroMasivo.COMPLETADO);
            procesoRepository.save(proceso);
            log.info("Reintegro masivo del evento {} finalizado: {} entradas acreditadas",
                    proceso.getEventoId(), proceso.getProcesados());
        } catch (Exception e) {
            registrarFallo(proceso, e);
        }
    }

    /** Acredita una entrada y la registra en el log de operaciones (TKY-35): OK o FAILED. */
    private void reintegrar(Long eventoId, EntradaReintegroDTO entrada) {
        try {
            acreditar(eventoId, entrada);
            OperationLogger.ok(Operation.REFUND, entrada.getClienteId(), ENTIDAD_REINTEGRO, entrada.getId());
        } catch (RuntimeException e) {
            OperationLogger.failed(Operation.REFUND, entrada.getClienteId(), ENTIDAD_REINTEGRO, entrada.getId(),
                    e.getMessage());
            throw e;
        }
    }

    private void acreditar(Long eventoId, EntradaReintegroDTO entrada) {
        if (entrada.getClienteId() == null || entrada.getPrecio() == null
                || entrada.getOrganizadorId() == null) {
            throw new IllegalStateException("La entrada " + entrada.getId()
                    + " no tiene los datos necesarios para el reintegro");
        }

        GenerarTransaccionRequest request = new GenerarTransaccionRequest()
                .ticketId(entrada.getId())
                .clienteId(entrada.getClienteId())
                .monto(entrada.getPrecio());

        TransaccionResponse credito = TransaccionesHeadersInterceptor.comoOrganizador(
                entrada.getOrganizadorId(), () -> transaccionesClient.generarCredito(request));

        if (credito == null || credito.getEstado() != EstadoTransaccion.COMPLETA) {
            throw new IllegalStateException("No se pudo acreditar la entrada " + entrada.getId()
                    + ": " + (credito == null ? "sin respuesta" : credito.getEstado()));
        }

        try {
            devolucionRepository.saveAndFlush(Devolucion.builder()
                    .clienteId(entrada.getClienteId())
                    .ticketId(entrada.getId())
                    .eventoId(eventoId)
                    .fecha(Instant.now())
                    .estado(EstadoDevolucion.APROBADA)
                    .transaccionId(credito.getId())
                    .motivo(MOTIVO)
                    .origen(ORIGEN)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("La entrada {} ya tenía su acreditación registrada", entrada.getId());
        }

        ticketClient.marcarDevuelto(entrada.getId());
    }

    private void registrarFallo(ReintegroMasivo proceso, Exception error) {
        int intentos = proceso.getIntentos() + 1;
        proceso.setIntentos(intentos);
        proceso.setFallos(proceso.getFallos() + 1);
        proceso.setReclamadoEl(null);

        if (intentos >= maxIntentos) {
            proceso.setEstado(EstadoReintegroMasivo.FALLIDO);
            log.error("Reintegro masivo del evento {} falló tras {} intentos (último ticket {})",
                    proceso.getEventoId(), intentos, proceso.getUltimoTicketId(), error);
        } else {
            proceso.setEstado(EstadoReintegroMasivo.PENDIENTE);
            log.warn("No se pudo completar el lote del reintegro del evento {} (intento {}). "
                            + "Se retoma desde el ticket {}",
                    proceso.getEventoId(), intentos, proceso.getUltimoTicketId(), error);
        }
        procesoRepository.save(proceso);
    }

    private void notificarCompradores(Long eventoId) {
        List<Long> clientes = devolucionRepository.findClientesReintegrados(eventoId, ORIGEN);
        for (Long clienteId : clientes) {
            NotificacionEntranteDTO mensaje = new NotificacionEntranteDTO();
            mensaje.setMensajeId("reintegro-masivo-" + eventoId + "-" + clienteId);
            mensaje.setMotivo(MotivoNotificacion.CANCELACION);
            mensaje.setEventoId(eventoId);
            mensaje.setDestinatarioId(clienteId);
            notificacionPublisher.publicar(mensaje);
        }
    }

    void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    void setMaxIntentos(int maxIntentos) {
        this.maxIntentos = maxIntentos;
    }

    void setTimeoutSegundos(long timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }
}
