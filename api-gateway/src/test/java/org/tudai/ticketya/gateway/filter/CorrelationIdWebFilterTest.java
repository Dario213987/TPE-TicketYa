package org.tudai.ticketya.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void sinHeader_generaUnIdLoEnviaAlServicioYLoDevuelve() {
        MockServerWebExchange exchange = exchange(null);
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, ex -> {
            downstream.set(ex);
            return ex.getResponse().setComplete();
        }).block();

        String enviado = downstream.get().getRequest().getHeaders().getFirst("X-Correlation-Id");
        assertThat(enviado).isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo(enviado);
        assertThat((String) exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE)).isEqualTo(enviado);
    }

    @Test
    void conHeaderValido_loConserva() {
        MockServerWebExchange exchange = exchange("mi-id-123");
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, ex -> {
            downstream.set(ex);
            return ex.getResponse().setComplete();
        }).block();

        assertThat(downstream.get().getRequest().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("mi-id-123");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("mi-id-123");
    }

    @Test
    void conHeaderInvalido_loReemplazaTantoHaciaElServicioComoEnLaRespuesta() {
        MockServerWebExchange exchange = exchange("a b");
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, ex -> {
            downstream.set(ex);
            return ex.getResponse().setComplete();
        }).block();

        String enviado = downstream.get().getRequest().getHeaders().getFirst("X-Correlation-Id");
        assertThat(enviado).isNotEqualTo("a b");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo(enviado);
    }

    @Test
    void siElServicioTambienDevuelveElHeader_laRespuestaNoLoDuplica() {
        MockServerWebExchange exchange = exchange("mi-id-123");

        filter.filter(exchange, ex -> {
            ex.getResponse().getHeaders().add("X-Correlation-Id", "mi-id-123");
            return ex.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getHeaders().getValuesAsList("X-Correlation-Id"))
                .containsExactly("mi-id-123");
    }

    private static MockServerWebExchange exchange(String correlationId) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/eventos");
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        return MockServerWebExchange.from(request.build());
    }
}