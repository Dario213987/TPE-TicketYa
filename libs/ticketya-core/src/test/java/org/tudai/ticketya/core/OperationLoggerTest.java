package org.tudai.ticketya.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(OperationLogger.LOGGER_NAME);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void ok_registraOperacionUsuarioEntidadEId() {
        OperationLogger.ok(Operation.PURCHASE, 42L, "Reserva", 7L);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("PURCHASE OK user=42 Reserva=7");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc).containsEntry("operation", "PURCHASE")
                .containsEntry("userId", "42")
                .containsEntry("entity", "Reserva")
                .containsEntry("entityId", "7")
                .containsEntry("result", "OK")
                .doesNotContainKey("reason");
    }

    @Test
    void failed_registraElMotivoComoWarn() {
        OperationLogger.failed(Operation.EVENT_CANCEL, 1L, "Evento", 3L, "ms-tickets no responde");

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage())
                .isEqualTo("EVENT_CANCEL FAILED user=1 Evento=3 reason=ms-tickets no responde");
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap()).containsEntry("result", "FAILED")
                .containsEntry("reason", "ms-tickets no responde");
    }

    @Test
    void compensated_registraElResultadoYElMotivo() {
        OperationLogger.compensated(Operation.PURCHASE, 42L, "Reserva", 7L, "débito rechazado");

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap()).containsEntry("result", "COMPENSATED")
                .containsEntry("reason", "débito rechazado");
    }

    @Test
    void failedSinMotivo_igualSeRegistraComoWarn() {
        OperationLogger.failed(Operation.ACCESS, 5L, "Ticket", "QR-1", null);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("reason");
    }

    @Test
    void sinUsuario_seRegistraComoAnonymous() {
        OperationLogger.ok(Operation.ACCESS, null, "Ticket", 9L);

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("userId", "anonymous");
    }

    @Test
    void elMotivoSeAcotaYSeLimpia() {
        OperationLogger.failed(Operation.REFUND, 1L, "Devolucion", 2L, "x".repeat(400) + "\nfalso");

        String reason = appender.list.get(0).getMDCPropertyMap().get("reason");
        assertThat(reason).hasSize(255).doesNotContain("\n");
    }

    @Test
    void alTerminar_quitaSusClavesDelMdcPeroRespetaElCorrelationId() {
        MDC.put(MdcKeys.CORRELATION_ID, "abc-123");

        OperationLogger.failed(Operation.PURCHASE, 42L, "Reserva", 7L, "rechazado");

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("correlationId", "abc-123");
        assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("correlationId");
    }
}
