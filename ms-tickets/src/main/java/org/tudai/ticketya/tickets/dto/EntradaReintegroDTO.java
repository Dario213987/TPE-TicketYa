package org.tudai.ticketya.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntradaReintegroDTO {

    private Long id;
    private Long clienteId;
    private Double precio;
    private String estado;
    private Long organizadorId;
    private Long transaccionId;
}
