package org.tudai.ticketya.tickets.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadResponseDTO {

    private Long eventoId;
    private boolean yaExistia;
    private int sectoresProcesados;
    private int asientosInsertados;
    private int asientosOmitidos;
    private Instant creadoEl;
}
