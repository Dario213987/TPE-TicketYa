package org.tudai.ticketya.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.tudai.ticketya.core.RequestLogger;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingWebFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogger.LOGGER_NAME);
    private final RequestLoggingWebFilter filter = new RequestLoggingWebFilter(new String[]{"/actuator/**"});
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
        MockServerWebExchange exchange = exchange("/api/eventos/7", "42");

        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return ex.getResponse().setComplete();
        }).block();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).startsWith("GET /api/eventos/7 -> 200 (").endsWith("user=42");
        assertThat(event.getMDCPropertyMap()).containsEntry("status", "200").containsKey("durationMs");
    }

    @Test
    void sinHeaderDeUsuario_seRegistraComoAnonymous() {
        filter.filter(exchange("/api/eventos", null), ex -> ex.getResponse().setComplete()).block();

        assertThat(appender.list.get(0).getFormattedMessage()).endsWith("user=anonymous");
    }

    @Test
    void siElServicioFalla_registraElStatusQueVaAResponderElGateway() {
        MockServerWebExchange exchange = exchange("/api/eventos", "42");

        filter.filter(exchange, ex -> Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)))
                .onErrorResume(e -> Mono.empty())
                .block();

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("status", "503");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void incluyeElCorrelationIdDelExchange() {
        MockServerWebExchange exchange = exchange("/api/eventos", null);
        exchange.getAttributes().put(CorrelationIdWebFilter.ATTRIBUTE, "abc-123");

        filter.filter(exchange, ex -> ex.getResponse().setComplete()).block();

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("correlationId", "abc-123");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void noIncluyeQueryStringNiHeadersSensibles() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/eventos?token=secreto123")
                        .header("Authorization", "Bearer tokenabc")
                        .build());

        filter.filter(exchange, ex -> ex.getResponse().setComplete()).block();

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage() + event.getMDCPropertyMap())
                .doesNotContain("secreto123", "tokenabc");
    }

    @Test
    void lasRutasExcluidasNoSeRegistran() {
        filter.filter(exchange("/actuator/health", null), ex -> ex.getResponse().setComplete()).block();

        assertThat(appender.list).isEmpty();
    }

    private static MockServerWebExchange exchange(String path, String userId) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        return MockServerWebExchange.from(request.build());
    }
}