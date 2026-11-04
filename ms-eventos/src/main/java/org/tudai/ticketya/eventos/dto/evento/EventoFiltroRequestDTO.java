package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoFiltroRequestDTO {
    private String tipo;
    private Long organizadorId;
    private String participante;
    private String ciudad;
    private String orden;

    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate fechaDesde;

    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate fechaHasta;
}
