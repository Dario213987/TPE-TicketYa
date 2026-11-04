package org.tudai.ticketya.devoluciones.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventoDTO {
    private Long id;
    private String nombre;
    private String estado;
    private LocalDate fecha;
    private LocalDateTime fechaHora;
}
