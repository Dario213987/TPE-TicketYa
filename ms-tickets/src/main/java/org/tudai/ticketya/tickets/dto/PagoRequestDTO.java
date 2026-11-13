package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoRequestDTO {

    @NotBlank(message = "El medio de pago es obligatorio")
    @Size(max = 30, message = "El medio de pago no puede tener más de 30 caracteres")
    private String medioPago;
}
