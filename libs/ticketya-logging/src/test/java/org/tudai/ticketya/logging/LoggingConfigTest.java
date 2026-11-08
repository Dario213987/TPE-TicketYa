package org.tudai.ticketya.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = LoggingConfigTest.TestApp.class, properties = {
        "spring.application.name=demo-svc",
        "ticketya.logging.dir=target/test-logs"
})
@ExtendWith(OutputCaptureExtension.class)
class LoggingConfigTest {

    private static final Path LOG_FILE = Path.of("target/test-logs/demo-svc/demo-svc.json.log");
    private static final Logger log = LoggerFactory.getLogger("test.logger");
    private final ObjectMapper mapper = new ObjectMapper();

    @Configuration
    static class TestApp {
    }

    @Test
    void elArchivoSeEscribeEnLaRutaDelServicio() {
        log.info("ruta-{}", UUID.randomUUID());

        assertThat(LOG_FILE).exists();
    }

    @Test
    void laLineaJsonTraeLosCamposPedidosYElMdc() throws Exception {
        String token = UUID.randomUUID().toString();
        MDC.put("correlationId", "abc-123");
        MDC.put("userId", "42");
        try {
            log.info("mensaje-json-{}", token);
        } finally {
            MDC.clear();
        }

        JsonNode json = findLine(token);

        assertThat(json.path("timestamp").asText()).isNotBlank();
        assertThat(json.path("level").asText()).isEqualTo("INFO");
        assertThat(json.path("service").asText()).isEqualTo("demo-svc");
        assertThat(json.path("logger").asText()).isEqualTo("test.logger");
        assertThat(json.path("message").asText()).contains(token);
        assertThat(json.path("correlationId").asText()).isEqualTo("abc-123");
        assertThat(json.path("userId").asText()).isEqualTo("42");
        assertThat(json.has("@timestamp")).isFalse();
        assertThat(json.has("@version")).isFalse();
    }

    @Test
    void todasLasLineasSonJsonValido_tambienLasDeExcepciones() throws Exception {
        String token = UUID.randomUUID().toString();
        log.error("fallo-{}", token, new IllegalStateException("boom"));

        for (String line : Files.readAllLines(LOG_FILE)) {
            assertThat(mapper.readTree(line).isObject()).as(line).isTrue();
        }
        assertThat(findLine(token).path("stack_trace").asText()).contains("IllegalStateException");
    }

    @Test
    void laConsolaEsTextoPlanoConServicioYCorrelationId(CapturedOutput output) {
        String token = UUID.randomUUID().toString();
        MDC.put("correlationId", "abc-123");
        try {
            log.info("mensaje-consola-{}", token);
        } finally {
            MDC.clear();
        }

        String line = output.getAll().lines()
                .filter(l -> l.contains(token))
                .findFirst()
                .orElseThrow();

        assertThat(line).doesNotStartWith("{")
                .contains("INFO")
                .contains("[demo-svc]")
                .contains("[abc-123]")
                .contains("test.logger");
    }

    @Test
    void rotaPorTamanoYFechaConRetencion() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        RollingFileAppender<?> appender =
                (RollingFileAppender<?>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("JSON_FILE");

        assertThat(appender.getRollingPolicy()).isInstanceOf(SizeAndTimeBasedRollingPolicy.class);
        assertThat(((SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy()).getMaxHistory())
                .isEqualTo(7);
    }

    private JsonNode findLine(String token) throws Exception {
        for (String line : Files.readAllLines(LOG_FILE)) {
            if (line.contains(token)) {
                return mapper.readTree(line);
            }
        }
        throw new AssertionError("No se encontró en el archivo una línea con " + token);
    }
}