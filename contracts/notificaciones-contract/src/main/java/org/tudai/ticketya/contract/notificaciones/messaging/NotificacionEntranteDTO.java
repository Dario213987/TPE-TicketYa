package org.tudai.ticketya.contract.notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificacionEntranteDTO {

    private String mensajeId;
    private MotivoNotificacion motivo;
    private long eventoId;
    private long destinatarioId;
    private DetalleConfirmacionCompra detalleCompra;

    public NotificacionEntranteDTO() {
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public void setMensajeId(String mensajeId) {
        this.mensajeId = mensajeId;
    }

    public MotivoNotificacion getMotivo() {
        return motivo;
    }

    public void setMotivo(MotivoNotificacion motivo) {
        this.motivo = motivo;
    }

    public long getEventoId() {
        return eventoId;
    }

    public void setEventoId(long eventoId) {
        this.eventoId = eventoId;
    }

    public long getDestinatarioId() {
        return destinatarioId;
    }

    public void setDestinatarioId(long destinatarioId) {
        this.destinatarioId = destinatarioId;
    }

    public DetalleConfirmacionCompra getDetalleCompra() {
        return detalleCompra;
    }

    public void setDetalleCompra(DetalleConfirmacionCompra detalleCompra) {
        this.detalleCompra = detalleCompra;
    }
}
