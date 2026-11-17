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
    private List<SectorClientDTO> sectores;

    public String getNombreLugar() {
        return lugar != null ? lugar.getNombre() : null;
    }
}
