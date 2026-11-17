package org.tudai.ticketya.contract.notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mensaje compartido entre productores y {@code ms-notificaciones}.
 * <p>
 * {@code mensajeId} es la clave de idempotencia. {@code destinatarioId} es obligatorio
 * para {@code CONFIRMACION_COMPRA} y puede ser nulo en avisos masivos. {@code detalle}
 * es polimorfico y acepta el nombre anterior {@code detalleCompra} por compatibilidad.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificacionEntranteDTO {

    private String mensajeId;
    private MotivoNotificacion motivo;
    private long eventoId;
    private Long destinatarioId;
    @JsonAlias("detalleCompra")
    private DetalleNotificacion detalle;

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

    public Long getDestinatarioId() {
        return destinatarioId;
    }

    public void setDestinatarioId(Long destinatarioId) {
        this.destinatarioId = destinatarioId;
    }

    public DetalleNotificacion getDetalle() {
        return detalle;
    }

    public void setDetalle(DetalleNotificacion detalle) {
        this.detalle = detalle;
    }

    /**
     * Valida que las confirmaciones de compra tengan destinatario.
     *
     * @throws IllegalArgumentException si falta el destinatario en una confirmacion
     */
    public void validarDestinatario() {
        if (motivo == MotivoNotificacion.CONFIRMACION_COMPRA && destinatarioId == null) {
            throw new IllegalArgumentException("CONFIRMACION_COMPRA requiere destinatarioId");
        }
    }
}
