package org.tudai.ticketya.gateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.tudai.ticketya.gateway.filter.CorrelationIdWebFilter;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler =
            new GatewayErrorHandler(Jackson2ObjectMapperBuilder.json().build());

    @Test
    void sinInstanciasDisponibles_responde503EstandarSinFiltrarNombresInternos() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for ms-eventos")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":503")
                .contains("Servicio no disponible")
                .doesNotContain("ms-eventos");
    }

    @Test
    void conexionRechazada_responde503() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new RuntimeException(new ConnectException("Connection refused"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rutaInexistente_responde404ConElMismoFormato() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Ruta no encontrada");
    }

    @Test
    void timeoutDelServicio_responde504() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void errorInesperado_responde500SinFiltrarDetalle() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new IllegalStateException("detalle interno secreto")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .doesNotContain("secreto")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void conCorrelationIdEnElExchange_loIncluyeEnElCuerpo() {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(CorrelationIdWebFilter.ATTRIBUTE, "abc-123");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"correlationId\":\"abc-123\"");
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/eventos").build());
    }
}