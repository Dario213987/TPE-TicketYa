package org.tudai.ticketya.notificaciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.tudai.ticketya.notificaciones.service.NotificacionService;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificacionDlqRecovererTest {

    @Mock
    private NotificacionService notificacionService;

    private NotificacionDlqRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new NotificacionDlqRecoverer(new ObjectMapper(), notificacionService);
    }

    @Test
    void marcaPendientesComoFallidosYRechazaSinReencolado() {
        Message mensaje = mensaje("{\"mensajeId\":\"MST-1-u\",\"destinatarioId\":20}");
        RuntimeException causa = new RuntimeException("reintentos agotados");

        assertThatThrownBy(() -> recoverer.recover(mensaje, causa))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("MST-1-u");

        verify(notificacionService).marcarPendientesComoFallidos("MST-1-u", causa);
    }

    @Test
    void sinMensajeIdNoTocaLaBaseYRechazaSinReencolado() {
        Message mensaje = mensaje("{\"destinatarioId\":20}");

        assertThatThrownBy(() -> recoverer.recover(mensaje, new RuntimeException("boom")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("sin mensajeId");

        verifyNoInteractions(notificacionService);
    }

    @Test
    void cuerpoInvalidoNoTocaLaBaseYRechazaSinReencolado() {
        Message mensaje = mensaje("no-json");

        assertThatThrownBy(() -> recoverer.recover(mensaje, new RuntimeException("boom")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(notificacionService);
    }

    private Message mensaje(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8));
    }
}
