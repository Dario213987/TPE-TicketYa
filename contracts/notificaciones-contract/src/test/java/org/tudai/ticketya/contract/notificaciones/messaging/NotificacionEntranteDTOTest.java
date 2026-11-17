package org.tudai.ticketya.contract.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificacionEntranteDTOTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializaSinDetalleCompra() throws Exception {
        String json = "{\"mensajeId\":\"MSE-1-00000000-0000-0000-0000-000000000001\","
                + "\"motivo\":\"CANCELACION\",\"eventoId\":10,\"destinatarioId\":20}";
        NotificacionEntranteDTO dto = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals("MSE-1-00000000-0000-0000-0000-000000000001", dto.getMensajeId());
        assertEquals(MotivoNotificacion.CANCELACION, dto.getMotivo());
        assertEquals(10L, dto.getEventoId());
        assertEquals(20L, dto.getDestinatarioId());
        assertNull(dto.getDetalleCompra());
    }

    @Test
    void roundtripConDetalleCompra() throws Exception {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMensajeId("MST-2-00000000-0000-0000-0000-000000000002");
        dto.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);
        dto.setEventoId(1L);
        dto.setDestinatarioId(2L);

        DetalleConfirmacionCompra detalle = new DetalleConfirmacionCompra();
        detalle.setSectorId(5L);
        detalle.setAsientos(List.of(new AsientoNotificacion(1, 10)));
        detalle.setCantidad(1);
        detalle.setMonto(new BigDecimal("1500.50"));
        detalle.setMoneda("ARS");
        dto.setDetalleCompra(detalle);

        String json = mapper.writeValueAsString(dto);
        NotificacionEntranteDTO leido = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals(MotivoNotificacion.CONFIRMACION_COMPRA, leido.getMotivo());
        assertNotNull(leido.getDetalleCompra());
        assertEquals(5L, leido.getDetalleCompra().getSectorId());
        assertEquals(1, leido.getDetalleCompra().getAsientos().size());
        assertEquals(1, leido.getDetalleCompra().getAsientos().get(0).getFila());
        assertEquals(10, leido.getDetalleCompra().getAsientos().get(0).getNumero());
        assertEquals(new BigDecimal("1500.50"), leido.getDetalleCompra().getMonto());
        assertEquals("ARS", leido.getDetalleCompra().getMoneda());
    }
}
