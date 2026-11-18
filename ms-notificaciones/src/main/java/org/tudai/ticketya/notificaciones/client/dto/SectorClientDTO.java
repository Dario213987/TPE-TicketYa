package org.tudai.ticketya.notificaciones.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos minimos de un sector de evento; se usa para resolver el nombre del sector
 * a partir del id que viaja en el detalle de compra.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectorClientDTO {
    private Long id;
    private String nombre;
}
