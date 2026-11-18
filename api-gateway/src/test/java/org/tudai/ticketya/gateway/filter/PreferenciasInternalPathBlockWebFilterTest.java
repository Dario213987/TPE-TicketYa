package org.tudai.ticketya.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PreferenciasInternalPathBlockWebFilterTest {

    private final PreferenciasInternalPathBlockWebFilter filter =
            new PreferenciasInternalPathBlockWebFilter();

    @Test
    void bloqueaElAccesoAlEndpointInternoDeDestinatarios() {
        MockServerWebExchange exchange = exchange("/api/preferencias/internal/destinatarios");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void bloqueaLaRutaInternaExacta() {
        MockServerWebExchange exchange = exchange("/api/preferencias/internal");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void dejaPasarLasRutasPublicasDePreferencias() {
        MockServerWebExchange exchange = exchange("/api/preferencias/mias");
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
        MockServerWebExchange exchange = exchange("/api/preferencias/internalizado");

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
