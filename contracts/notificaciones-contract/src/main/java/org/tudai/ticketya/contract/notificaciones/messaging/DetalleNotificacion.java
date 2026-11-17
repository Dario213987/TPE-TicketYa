package org.tudai.ticketya.contract.notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marcador polimorfico para el detalle de una notificacion.
 * <p>
 * El discriminante {@code tipo} permite agregar detalles para nuevos motivos sin
 * cambiar el campo unico {@code detalle} del mensaje. Si el detalle no trae tipo, se
 * interpreta como {@link DetalleConfirmacionCompra} por compatibilidad.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "tipo",
        defaultImpl = DetalleConfirmacionCompra.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DetalleConfirmacionCompra.class, name = "CONFIRMACION_COMPRA")
})
public interface DetalleNotificacion {
}
