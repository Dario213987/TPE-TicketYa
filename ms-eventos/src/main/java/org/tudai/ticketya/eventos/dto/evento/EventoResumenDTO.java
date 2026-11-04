package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tudai.ticketya.eventos.domain.EstadoEvento;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoResumenDTO {

    private Long id;
    private String nombre;
    private String tipo;
    private LocalDate fecha;
    private EstadoEvento estado;
}
