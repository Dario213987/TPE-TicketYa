package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asiento {
    private Integer fila;
    private Integer numero;
}
