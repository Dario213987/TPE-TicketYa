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

class RequestLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogger.LOGGER_NAME);
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
    void registraMetodoRutaUsuarioStatusYDuracion() {
        RequestLogger.log("GET", "/api/eventos/7", "42", 200, 15);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("GET /api/eventos/7 -> 200 (15 ms) user=42");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc).containsEntry("method", "GET")
                .containsEntry("path", "/api/eventos/7")
                .containsEntry("userId", "42")
                .containsEntry("status", "200")
                .containsEntry("durationMs", "15");
    }

    @Test
    void sinUsuario_seRegistraComoAnonymous() {
        RequestLogger.log("GET", "/api/eventos", null, 200, 1);
        RequestLogger.log("GET", "/api/eventos", "   ", 200, 1);

        assertThat(appender.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).endsWith("user=anonymous");
            assertThat(event.getMDCPropertyMap()).containsEntry("userId", "anonymous");
        });
    }

    @Test
    void error5xx_seRegistraComoWarn() {
        RequestLogger.log("POST", "/api/reservas", "42", 503, 30);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void error4xx_seRegistraComoInfo() {
        RequestLogger.log("GET", "/api/eventos/99", "42", 404, 3);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void elUsuarioSeAcotaYSeLimpia() {
        RequestLogger.log("GET", "/x", "a".repeat(200) + "\nfalso", 200, 1);

        String user = appender.list.get(0).getMDCPropertyMap().get("userId");
        assertThat(user).hasSize(64).doesNotContain("\n");
    }

    @Test
    void alTerminar_quitaSusClavesDelMdcPeroRespetaElCorrelationId() {
        MDC.put(MdcKeys.CORRELATION_ID, "abc-123");

        RequestLogger.log("GET", "/x", "42", 200, 1);

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("correlationId", "abc-123");
        assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("correlationId");
    }
}