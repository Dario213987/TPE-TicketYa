package org.tudai.ticketya.notificaciones.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sector de un evento obtenido vía Feign (ms-eventos), usado para resolver el nombre del sector
 * de compra a partir de detalleCompra.sectorId.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectorClientDTO {
    private Long id;
    private String nombre;
}
