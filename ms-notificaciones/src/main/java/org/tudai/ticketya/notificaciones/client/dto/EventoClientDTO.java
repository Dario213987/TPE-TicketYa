package org.tudai.ticketya.notificaciones.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EventoClientDTO {
    private Long id;
    private String nombre;
    private LocalDateTime fecha;
    private LugarClientDTO lugar;
    /** Sectores del evento (id/nombre) para cruzar con sectorId del detalle de compra. */
    private List<SectorClientDTO> sectores;

    public String getNombreLugar() {
        return lugar != null ? lugar.getNombre() : null;
    }
}
