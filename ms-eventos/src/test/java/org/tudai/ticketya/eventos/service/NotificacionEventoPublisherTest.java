package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificacionEventoPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificacionEventoPublisher publisher;

    @Test
    void publicarCANCELACION_usaMensajeMasivoEstable() {
        publisher.publicar(MotivoNotificacion.CANCELACION, 10L, "MSE-CANCELACION-10");

        NotificacionEntranteDTO mensaje = capturarMensaje();
        assertThat(mensaje.getMensajeId()).isEqualTo("MSE-CANCELACION-10");
        assertThat(mensaje.getMotivo()).isEqualTo(MotivoNotificacion.CANCELACION);
        assertThat(mensaje.getEventoId()).isEqualTo(10L);
        assertThat(mensaje.getDestinatarioId()).isNull();
        assertThat(mensaje.getDetalle()).isNull();
    }

    @Test
    void publicarEdicion_usaElMensajeIdDelCambio() {
        publisher.publicar(MotivoNotificacion.EDICION, 10L, "MSE-EDICION-1731700000000-uuid");

        NotificacionEntranteDTO mensaje = capturarMensaje();
        assertThat(mensaje.getMensajeId()).isEqualTo("MSE-EDICION-1731700000000-uuid");
        assertThat(mensaje.getMotivo()).isEqualTo(MotivoNotificacion.EDICION);
        assertThat(mensaje.getEventoId()).isEqualTo(10L);
        assertThat(mensaje.getDestinatarioId()).isNull();
    }

    private NotificacionEntranteDTO capturarMensaje() {
        ArgumentCaptor<NotificacionEntranteDTO> captor =
                ArgumentCaptor.forClass(NotificacionEntranteDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(ColasNotificacion.COLA_ENVIAR), captor.capture());
        return captor.getValue();
    }
}
