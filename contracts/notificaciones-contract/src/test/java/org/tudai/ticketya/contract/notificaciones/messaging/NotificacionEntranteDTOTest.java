package org.tudai.ticketya.contract.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificacionEntranteDTOTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializaCancelacionSinDestinatario() throws Exception {
        String json = "{\"mensajeId\":\"MSE-CANCELACION-10\","
                + "\"motivo\":\"CANCELACION\",\"eventoId\":10}";
        NotificacionEntranteDTO dto = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals("MSE-CANCELACION-10", dto.getMensajeId());
        assertEquals(MotivoNotificacion.CANCELACION, dto.getMotivo());
        assertEquals(10L, dto.getEventoId());
        assertNull(dto.getDestinatarioId());
        assertNull(dto.getDetalle());
    }

    @Test
    void deserializaSinDetalleCompra() throws Exception {
        String json = "{\"mensajeId\":\"MSE-1-00000000-0000-0000-0000-000000000001\","
                + "\"motivo\":\"CANCELACION\",\"eventoId\":10,\"destinatarioId\":20}";
        NotificacionEntranteDTO dto = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals("MSE-1-00000000-0000-0000-0000-000000000001", dto.getMensajeId());
        assertEquals(MotivoNotificacion.CANCELACION, dto.getMotivo());
        assertEquals(10L, dto.getEventoId());
        assertEquals(20L, dto.getDestinatarioId().longValue());
        assertNull(dto.getDetalle());
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
        dto.setDetalle(detalle);

        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("\"tipo\":\"CONFIRMACION_COMPRA\""), json);
        NotificacionEntranteDTO leido = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertEquals(MotivoNotificacion.CONFIRMACION_COMPRA, leido.getMotivo());
        assertInstanceOf(DetalleConfirmacionCompra.class, leido.getDetalle());
        DetalleConfirmacionCompra detalleLeido = (DetalleConfirmacionCompra) leido.getDetalle();
        assertEquals(5L, detalleLeido.getSectorId());
        assertEquals(1, detalleLeido.getAsientos().size());
        assertEquals(1, detalleLeido.getAsientos().get(0).getFila());
        assertEquals(10, detalleLeido.getAsientos().get(0).getNumero());
        assertEquals(new BigDecimal("1500.50"), detalleLeido.getMonto());
        assertEquals("ARS", detalleLeido.getMoneda());
    }

    @Test
    void deserializaAliasDetalleCompra() throws Exception {
        String json = "{\"mensajeId\":\"MST-3-00000000-0000-0000-0000-000000000003\","
                + "\"motivo\":\"CONFIRMACION_COMPRA\",\"eventoId\":1,\"destinatarioId\":2,"
                + "\"detalleCompra\":{\"tipo\":\"CONFIRMACION_COMPRA\",\"sectorId\":5,\"cantidad\":1}}";

        NotificacionEntranteDTO dto = mapper.readValue(json, NotificacionEntranteDTO.class);

        assertInstanceOf(DetalleConfirmacionCompra.class, dto.getDetalle());
        assertEquals(5L, ((DetalleConfirmacionCompra) dto.getDetalle()).getSectorId());
    }

    @Test
    void confirmacionCompraRequiereDestinatario() {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);

        assertThrows(IllegalArgumentException.class, dto::validarDestinatario);

        dto.setDestinatarioId(20L);
        assertDoesNotThrow(dto::validarDestinatario);
    }

    @Test
    void cancelacionPermiteDestinatarioAusente() {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMotivo(MotivoNotificacion.CANCELACION);

        assertDoesNotThrow(dto::validarDestinatario);
    }
}
