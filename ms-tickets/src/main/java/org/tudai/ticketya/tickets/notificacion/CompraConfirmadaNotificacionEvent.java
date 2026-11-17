package org.tudai.ticketya.tickets.notificacion;

import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evento de aplicación publicado al confirmar una compra. Se procesa tras el commit de la
 * transacción (AFTER_COMMIT) para disparar la notificación sin afectar la confirmación.
 */
public class CompraConfirmadaNotificacionEvent {

    private final Long reservaId;
    private final Long eventoId;
    private final Long destinatarioId;
    private final Long sectorId;
    private final List<AsientoNotificacion> asientos;
    private final Integer cantidad;
    private final BigDecimal monto;
    private final String moneda;

    public CompraConfirmadaNotificacionEvent(Long reservaId, Long eventoId, Long destinatarioId, Long sectorId,
                                             List<AsientoNotificacion> asientos, Integer cantidad,
                                             BigDecimal monto, String moneda) {
        this.reservaId = reservaId;
        this.eventoId = eventoId;
        this.destinatarioId = destinatarioId;
        this.sectorId = sectorId;
        this.asientos = asientos;
        this.cantidad = cantidad;
        this.monto = monto;
        this.moneda = moneda;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Long getDestinatarioId() {
        return destinatarioId;
    }

    public Long getSectorId() {
        return sectorId;
    }

    public List<AsientoNotificacion> getAsientos() {
        return asientos;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getMoneda() {
        return moneda;
    }
}
