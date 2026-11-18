package org.tudai.ticketya.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import static org.junit.jupiter.api.Assertions.*;

class NotificacionEntranteDeserializacionTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializaConDetalleCompra() throws Exception {
        String json = "{"
                + "\"mensajeId\":\"MST-1-00000000-0000-0000-0000-000000000001\","
                + "\"motivo\":\"CONFIRMACION_COMPRA\","
                + "\"eventoId\":10,"
                + "\"destinatarioId\":20,"
                + "\"detalleCompra\":{\"sectorId\":5,\"asientos\":[{\"fila\":1,\"numero\":2}],\"cantidad\":1,\"monto\":1500.5,\"moneda\":\"ARS\"}"
                + "}";
        NotificacionEntranteDTO dto = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals(MotivoNotificacion.CONFIRMACION_COMPRA, dto.getMotivo());
        assertInstanceOf(DetalleConfirmacionCompra.class, dto.getDetalle());
        DetalleConfirmacionCompra detalle = (DetalleConfirmacionCompra) dto.getDetalle();
        assertEquals(5L, detalle.getSectorId());
        assertEquals(1, detalle.getAsientos().size());
        assertEquals(1, detalle.getAsientos().get(0).getFila());
    }

    @Test
    void deserializaSinDetalleCompra() throws Exception {
        String json = "{"
                + "\"mensajeId\":\"MST-2-00000000-0000-0000-0000-000000000002\","
                + "\"motivo\":\"CANCELACION\","
                + "\"eventoId\":10,"
                + "\"destinatarioId\":20"
                + "}";
        NotificacionEntranteDTO dto = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals(MotivoNotificacion.CANCELACION, dto.getMotivo());
        assertNull(dto.getDetalle());
    }
}
