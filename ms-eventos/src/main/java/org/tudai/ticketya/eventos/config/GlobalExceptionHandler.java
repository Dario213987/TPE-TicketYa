package org.tudai.ticketya.eventos.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(SectoresSinPrecioException.class)
    public ResponseEntity<ProblemDetail> handleSectoresSinPrecio(SectoresSinPrecioException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Los siguientes sectores no tienen precio: " + ex.nombres());
        problem.setTitle("No se puede publicar el evento");
        problem.setProperty("sectores", ex.getSectores());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}