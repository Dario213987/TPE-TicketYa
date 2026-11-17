package org.tudai.ticketya.notificaciones.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UsuarioClientDTO {
    private Long id;
    private String email;
    private String nombre;
}
