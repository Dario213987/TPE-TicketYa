package org.tudai.ticketya.eventos.dto.evento;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.tudai.ticketya.eventos.domain.EstadoEvento;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResumenDTO {
    private Long id;
    private String nombre;
    private String tipo;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate fecha;
    private EstadoEvento estado;
}
