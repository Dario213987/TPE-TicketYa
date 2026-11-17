package org.tudai.ticketya.notificaciones.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos minimos del lugar de un evento.
 */
@Getter
@Setter
@NoArgsConstructor
public class LugarClientDTO {
    private Long id;
    private String nombre;
}
