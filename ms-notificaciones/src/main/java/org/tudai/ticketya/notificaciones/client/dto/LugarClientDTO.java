package org.tudai.ticketya.notificaciones.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos minimos del lugar de un evento.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LugarClientDTO {
    private Long id;
    private String nombre;
    private String ciudad;
}
