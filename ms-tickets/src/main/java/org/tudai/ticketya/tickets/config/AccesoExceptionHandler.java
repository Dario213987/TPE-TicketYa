package org.tudai.ticketya.tickets.config;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.tickets.exception.AccesoRechazadoException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AccesoExceptionHandler {

    @ExceptionHandler(AccesoRechazadoException.class)
    public ResponseEntity<ProblemDetail> handle(AccesoRechazadoException ex) {
        HttpStatus status = ex.getCodigo().status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(status == HttpStatus.NOT_FOUND ? "Recurso no encontrado" : "Conflicto");
        problem.setProperty("codigo", ex.getCodigo().name());

        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
