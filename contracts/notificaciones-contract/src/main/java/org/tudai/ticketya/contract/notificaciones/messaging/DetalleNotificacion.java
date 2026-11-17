package org.tudai.ticketya.contract.notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
