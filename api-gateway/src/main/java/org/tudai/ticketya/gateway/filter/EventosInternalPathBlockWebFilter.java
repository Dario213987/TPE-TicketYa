package org.tudai.ticketya.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Bloquea el acceso externo a los endpoints internos de ms-eventos.
 * <p>
 * Los endpoints {@code /api/eventos/internal/**} son consumidos únicamente
 * servicio-a-servicio (por ejemplo, desde ms-tickets) y no deben quedar expuestos
 * a través del gateway. Se responde 404 para no revelar su existencia.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class EventosInternalPathBlockWebFilter implements WebFilter {

    private static final String INTERNAL_EVENTOS_PREFIX = "/api/eventos/internal/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(INTERNAL_EVENTOS_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
