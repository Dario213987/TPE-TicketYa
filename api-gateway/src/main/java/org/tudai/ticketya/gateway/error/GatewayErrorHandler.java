package org.tudai.ticketya.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.gateway.filter.CorrelationIdWebFilter;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

@Component
@Order(-2) // para ir antes que el handler por defecto de Spring Boot (-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper mapper;

    public GatewayErrorHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        String path = exchange.getRequest().getPath().value();
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE);

        if (correlationId != null) {
            MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        }
        try {
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                log.error("Error no controlado en {}", path, ex);
            } else {
                log.warn("{} {} -> {} ({})", exchange.getRequest().getMethod(), path, status.value(), ex.getMessage());
            }
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detailFor(status));
        problem.setTitle(titleFor(status));
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }

        byte[] body;
        try {
            body = mapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            return Mono.error(ex);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private static HttpStatus resolveStatus(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ResponseStatusException rse) {
                HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
                return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (t instanceof ConnectException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String titleFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Ruta no encontrada";
            case SERVICE_UNAVAILABLE -> "Servicio no disponible";
            case GATEWAY_TIMEOUT -> "Tiempo de espera agotado";
            default -> status.is4xxClientError() ? "Solicitud inválida" : "Error interno";
        };
    }

    private static String detailFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "La ruta solicitada no existe.";
            case SERVICE_UNAVAILABLE -> "El servicio no está disponible en este momento. Intente nuevamente más tarde.";
            case GATEWAY_TIMEOUT -> "El servicio tardó demasiado en responder.";
            default -> status.is4xxClientError()
                    ? "La solicitud no pudo ser procesada."
                    : "Ocurrió un error inesperado. Intente nuevamente más tarde.";
        };
    }
}