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
 * Bloquea el acceso externo a los endpoints internos de ms-tickets.
 * <p>
 * Los endpoints {@code /api/tickets/internal} y {@code /api/tickets/internal/**}
 * son consumidos únicamente servicio-a-servicio (por ejemplo, desde ms-eventos para
 * cerrar o reabrir la venta) y no deben quedar expuestos a través del gateway.
 * Se responde 404 para no revelar su existencia.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
public class TicketsInternalPathBlockWebFilter implements WebFilter {

    private static final String INTERNAL_TICKETS_PATH = "/api/tickets/internal";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals(INTERNAL_TICKETS_PATH) || path.startsWith(INTERNAL_TICKETS_PATH + "/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
