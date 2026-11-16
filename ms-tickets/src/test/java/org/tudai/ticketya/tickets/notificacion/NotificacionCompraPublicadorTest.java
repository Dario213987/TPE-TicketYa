package org.tudai.ticketya.tickets.notificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.tickets.config.RabbitNotificacionesConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionCompraPublicadorTest {

    @Mock
    private NotificacionCompraMensajeFactory factory;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificacionCompraPublicador publicador;

    @Test
    void produceYPublicaEnColaEnviar() {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMensajeId("MST-1-u");
        dto.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);
        dto.setEventoId(10L);
        dto.setDestinatarioId(20L);
        when(factory.crear(eq(10L), eq(20L), eq(5L), any(), eq(2), any(), eq("ARS"))).thenReturn(dto);

        publicador.producirConfirmacionCompra(10L, 20L, 5L, List.of(new AsientoNotificacion(1, 1)), 2, new BigDecimal("100"), "ARS");

        ArgumentCaptor<NotificacionEntranteDTO> captor = ArgumentCaptor.forClass(NotificacionEntranteDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitNotificacionesConfig.COLA_ENVIAR), captor.capture());
        assertEquals("MST-1-u", captor.getValue().getMensajeId());
        assertEquals(MotivoNotificacion.CONFIRMACION_COMPRA, captor.getValue().getMotivo());
    }

    @Test
    void falloDePublicacionNoPropagaExcepcion() {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMensajeId("MST-2-u");
        dto.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);
        when(factory.crear(any(), any(), any(), any(), any(), any(), any())).thenReturn(dto);
        doThrow(new RuntimeException("broker caido")).when(rabbitTemplate)
                .convertAndSend(eq(RabbitNotificacionesConfig.COLA_ENVIAR), any(Object.class));

        assertDoesNotThrow(() -> publicador.producirConfirmacionCompra(1L, 2L, 3L, List.of(), 1, null, null));
    }
}
