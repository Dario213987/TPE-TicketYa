package org.tudai.ticketya.tickets.notificacion;

import org.junit.jupiter.api.Test;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificacionCompraMensajeFactoryTest {

    private final NotificacionCompraMensajeFactory factory = new NotificacionCompraMensajeFactory();

    @Test
    void creaMensajeConDatosDeCompraYMensajeIdMst() {
        NotificacionEntranteDTO dto = factory.crear(7L, 10L, 20L, 5L,
                List.of(new AsientoNotificacion(1, 2)), 1, new BigDecimal("1500.00"), "ARS");

        assertEquals("MST-RESERVA-7", dto.getMensajeId());
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
    void generaIdEstableParaLaMismaReserva() {
        NotificacionEntranteDTO a = factory.crear(7L, 1L, 2L, 3L, List.of(), 0, null, null);
        NotificacionEntranteDTO b = factory.crear(7L, 1L, 2L, 3L, List.of(), 0, null, null);
        assertEquals(a.getMensajeId(), b.getMensajeId());
    }

    @Test
    void reservasDistintasGeneranMensajesIdsDistintos() {
        NotificacionEntranteDTO a = factory.crear(1L, 10L, 20L, 5L, List.of(), 1, null, "ARS");
        NotificacionEntranteDTO b = factory.crear(2L, 10L, 20L, 5L, List.of(), 1, null, "ARS");
        assertNotEquals(a.getMensajeId(), b.getMensajeId());
    }
}
