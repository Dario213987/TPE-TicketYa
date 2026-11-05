package org.tudai.ticketya.tickets.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrEntradaDTO {

    private Long ticketId;

    /** Contenido a codificar en el QR: el código único del ticket. */
    private String contenido;
}
