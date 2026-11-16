package org.tudai.ticketya.notificaciones.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class EventoClientDTO {
    private Long id;
    private String nombre;
    private LocalDateTime fecha;
    private LugarClientDTO lugar;

    public String getNombreLugar() {
        return lugar != null ? lugar.getNombre() : null;
    }
}
