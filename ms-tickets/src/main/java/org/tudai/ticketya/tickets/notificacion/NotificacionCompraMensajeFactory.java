package org.tudai.ticketya.tickets.notificacion;

import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MensajeIdNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma el mensaje de notificación de confirmación de compra (id + DTO). No realiza envío.
 */
@Component
public class NotificacionCompraMensajeFactory {

    public static final String PREFIJO_NOTIFICACION = "MST";

    /**
     * Genera mensajeId derivado de la reserva y construye el DTO con motivo CONFIRMACION_COMPRA y detalle de compra.
     */
    public NotificacionEntranteDTO crear(Long reservaId, Long eventoId, Long destinatarioId, Long sectorId,
                                         List<AsientoNotificacion> asientos, Integer cantidad,
                                         BigDecimal monto, String moneda) {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMensajeId(MensajeIdNotificacion.generarDerivado(PREFIJO_NOTIFICACION, "RESERVA", reservaId));
        dto.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);
        dto.setEventoId(eventoId);
        dto.setDestinatarioId(destinatarioId);

        DetalleConfirmacionCompra detalle = new DetalleConfirmacionCompra();
        detalle.setSectorId(sectorId);
        detalle.setAsientos(asientos != null ? asientos : new ArrayList<>());
        detalle.setCantidad(cantidad);
        detalle.setMonto(monto);
        detalle.setMoneda(moneda);
        dto.setDetalle(detalle);
        return dto;
    }
}
