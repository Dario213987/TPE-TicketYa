package org.tudai.ticketya.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TicketsInternalPathBlockWebFilterTest {

    private final TicketsInternalPathBlockWebFilter filter = new TicketsInternalPathBlockWebFilter();

    @Test
    void bloqueaElAccesoAUnEndpointInternoDeTickets() {
        MockServerWebExchange exchange = exchange("/api/tickets/internal/eventos/1/cancelar");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void bloqueaTambienElEndpointInternoDeFinalizar() {
        MockServerWebExchange exchange = exchange("/api/tickets/internal/eventos/1/finalizar");

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void bloqueaLaRutaInternaExacta() {
        MockServerWebExchange exchange = exchange("/api/tickets/internal");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void dejaPasarLasRutasPublicasDeTickets() {
        MockServerWebExchange exchange = exchange("/api/tickets/eventos/1/fila");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void dejaPasarLasRutasQueSoloContienenLaPalabraInternal() {
        MockServerWebExchange exchange = exchange("/api/tickets/internalizado");

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.put(path).build());
    }
}
