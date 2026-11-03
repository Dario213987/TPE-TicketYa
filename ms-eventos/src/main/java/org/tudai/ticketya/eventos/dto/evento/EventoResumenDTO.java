package org.tudai.ticketya.eventos.dto.evento;

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
    private LocalDate fecha;
    private EstadoEvento estado;
}
