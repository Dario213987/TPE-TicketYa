package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.tudai.ticketya.tickets.domain.MedioPago;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoRequestDTO {

    @NotNull(message = "El medio de pago es obligatorio")
    private MedioPago medioPago;
}
