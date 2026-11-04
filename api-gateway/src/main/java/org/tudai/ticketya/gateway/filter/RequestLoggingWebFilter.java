package org.tudai.ticketya.gateway.filter;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.core.RequestLogger;
import org.tudai.ticketya.gateway.error.GatewayErrorHandler;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "ticketya.logging.requests.enabled", havingValue = "true", matchIfMissing = true)
public class RequestLoggingWebFilter implements WebFilter {

    private final String[] excludePaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RequestLoggingWebFilter(
            @Value("${ticketya.logging.requests.exclude-paths:/actuator/**}") String[] excludePaths) {
        this.excludePaths = excludePaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();   // sin query string
        if (isExcluded(path)) {
            return chain.filter(exchange);
        }

        long start = System.nanoTime();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        return chain.filter(exchange)
                .doOnError(failure::set)
                .doFinally(signal -> logRequest(exchange, path, start, failure.get()));
    }

    private void logRequest(ServerWebExchange exchange, String path, long start, Throwable failure) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        int status = failure != null
                ? GatewayErrorHandler.resolveStatus(failure).value()
                : statusOf(exchange);
        String userId = exchange.getRequest().getHeaders().getFirst(HeaderNames.USER_ID);
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE);

        if (correlationId != null) {
            MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        }
        try {
            RequestLogger.log(exchange.getRequest().getMethod().name(), path, userId, status, durationMs);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }

    private static int statusOf(ServerWebExchange exchange) {
        HttpStatusCode code = exchange.getResponse().getStatusCode();
        return code != null ? code.value() : 200;
    }

    private boolean isExcluded(String path) {
        for (String pattern : excludePaths) {
            if (matcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }
}