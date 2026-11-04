package org.tudai.ticketya.eventos.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
