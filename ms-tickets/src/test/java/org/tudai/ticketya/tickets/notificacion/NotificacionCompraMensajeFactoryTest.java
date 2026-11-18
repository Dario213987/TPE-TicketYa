package org.tudai.ticketya.tickets.notificacion;

import org.junit.jupiter.api.Test;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificacionCompraMensajeFactoryTest {

    private final NotificacionCompraMensajeFactory factory = new NotificacionCompraMensajeFactory();

    @Test
    void creaMensajeConDatosDeCompraYMensajeIdDerivadoDeReserva() {
        NotificacionEntranteDTO dto = factory.crear(99L, 10L, 20L, 5L,
                List.of(new AsientoNotificacion(1, 2)), 1, new BigDecimal("1500.00"), "ARS");

        assertEquals("MST-RESERVA-99", dto.getMensajeId());
        assertEquals(MotivoNotificacion.CONFIRMACION_COMPRA, dto.getMotivo());
        assertEquals(10L, dto.getEventoId());
        assertEquals(20L, dto.getDestinatarioId());
        assertNotNull(dto.getDetalleCompra());
        assertEquals(5L, dto.getDetalleCompra().getSectorId());
        assertEquals(1, dto.getDetalleCompra().getAsientos().size());
        assertEquals(1, dto.getDetalleCompra().getAsientos().get(0).getFila());
        assertEquals(2, dto.getDetalleCompra().getAsientos().get(0).getNumero());
        assertEquals(1, dto.getDetalleCompra().getCantidad());
        assertEquals(new BigDecimal("1500.00"), dto.getDetalleCompra().getMonto());
        assertEquals("ARS", dto.getDetalleCompra().getMoneda());
    }

    @Test
    void mismaReservaGeneraMismoMensajeId() {
        NotificacionEntranteDTO a = factory.crear(99L, 10L, 20L, 5L, List.of(), 1, null, "ARS");
        NotificacionEntranteDTO b = factory.crear(99L, 10L, 20L, 5L, List.of(), 1, null, "ARS");
        assertEquals(a.getMensajeId(), b.getMensajeId());
    }

    @Test
    void reservasDistintasGeneranMensajesIdsDistintos() {
        NotificacionEntranteDTO a = factory.crear(1L, 10L, 20L, 5L, List.of(), 1, null, "ARS");
        NotificacionEntranteDTO b = factory.crear(2L, 10L, 20L, 5L, List.of(), 1, null, "ARS");
        assertNotEquals(a.getMensajeId(), b.getMensajeId());
    }
}
