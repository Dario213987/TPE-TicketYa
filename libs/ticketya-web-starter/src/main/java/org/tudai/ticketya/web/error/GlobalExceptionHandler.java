package org.tudai.ticketya.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.core.exception.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldViolation(String field, String message) {}

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Object> handleBadRequest(BadRequestException ex, WebRequest request) {
        return respond(ex, HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
        return respond(ex, HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> handleConflict(ConflictException ex, WebRequest request) {
        return respond(ex, HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Object> handleForbidden(ForbiddenException ex, WebRequest request) {
        return respond(ex, HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Error no controlado ({})", request.getDescription(false), ex);
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intente nuevamente más tarde.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new FieldViolation(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new FieldViolation(e.getObjectName(), e.getDefaultMessage())));
        errors.sort(Comparator.comparing(FieldViolation::field));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud contiene campos inválidos");
        problem.setTitle("Validación fallida");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(RemoteServiceException.class)
    ResponseEntity<Object> handleRemote(RemoteServiceException ex, WebRequest request) {
        log.error("Error en servicio remoto (status {}): {}", ex.getRemoteStatus(), ex.getMessage());
        return respond(ex, HttpStatus.BAD_GATEWAY, "Error en servicio dependiente",
                "Un servicio del que depende esta operación respondió con error.", request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
            if (correlationId != null) {
                problem.setProperty("correlationId", correlationId);
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> respond(Exception ex, HttpStatus status, String title,
                                           String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }
}
