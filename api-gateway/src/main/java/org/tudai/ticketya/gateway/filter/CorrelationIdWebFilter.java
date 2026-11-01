package org.tudai.ticketya.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.tudai.ticketya.core.CorrelationId;
import org.tudai.ticketya.core.HeaderNames;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String ATTRIBUTE = "ticketya.correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = CorrelationId.resolve(exchange.getRequest().getHeaders().getFirst(HeaderNames.CORRELATION_ID));
        exchange.getAttributes().put(ATTRIBUTE, id);

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HeaderNames.CORRELATION_ID, id);
            return Mono.empty();
        });

        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HeaderNames.CORRELATION_ID, id)))
                .build();
        return chain.filter(mutated);
    }
}