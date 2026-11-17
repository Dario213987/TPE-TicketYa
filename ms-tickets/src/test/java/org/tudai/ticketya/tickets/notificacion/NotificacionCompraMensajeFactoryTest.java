package org.tudai.ticketya.tickets.notificacion;

import org.junit.jupiter.api.Test;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class NotificacionCompraMensajeFactoryTest {

    private static final Pattern FORMATO_ID = Pattern.compile(
            "^MST-\\d+-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final NotificacionCompraMensajeFactory factory = new NotificacionCompraMensajeFactory();

    @Test
    void creaMensajeConDatosDeCompraYMensajeIdMst() {
        NotificacionEntranteDTO dto = factory.crear(10L, 20L, 5L,
                List.of(new AsientoNotificacion(1, 2)), 1, new BigDecimal("1500.00"), "ARS");

        assertNotNull(dto.getMensajeId());
        assertTrue(FORMATO_ID.matcher(dto.getMensajeId()).matches(), "mensajeId: " + dto.getMensajeId());
        assertEquals(MotivoNotificacion.CONFIRMACION_COMPRA, dto.getMotivo());
        assertEquals(10L, dto.getEventoId());
        assertEquals(20L, dto.getDestinatarioId().longValue());
        assertInstanceOf(DetalleConfirmacionCompra.class, dto.getDetalle());
        DetalleConfirmacionCompra detalle = (DetalleConfirmacionCompra) dto.getDetalle();
        assertEquals(5L, detalle.getSectorId());
        assertEquals(1, detalle.getAsientos().size());
        assertEquals(1, detalle.getAsientos().get(0).getFila());
        assertEquals(2, detalle.getAsientos().get(0).getNumero());
        assertEquals(1, detalle.getCantidad());
        assertEquals(new BigDecimal("1500.00"), detalle.getMonto());
        assertEquals("ARS", detalle.getMoneda());
    }

    @Test
    void generaIdsDistintos() {
        NotificacionEntranteDTO a = factory.crear(1L, 2L, 3L, List.of(), 0, null, null);
        NotificacionEntranteDTO b = factory.crear(1L, 2L, 3L, List.of(), 0, null, null);
        assertNotEquals(a.getMensajeId(), b.getMensajeId());
    }
}
