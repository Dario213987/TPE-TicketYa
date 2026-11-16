package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.Min;
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
public class HabilitacionRequestDTO {

    private Boolean habilitada;

    @Min(value = 1, message = "El tamaño de tanda debe ser mayor a 0")
    private Integer tamanoTanda;

    @Min(value = 1, message = "El intervalo debe ser mayor a 0 segundos")
    private Integer intervaloSegundos;

    @Min(value = 1, message = "El TTL del pase debe ser mayor a 0 segundos")
    private Integer ttlPaseSegundos;
}
