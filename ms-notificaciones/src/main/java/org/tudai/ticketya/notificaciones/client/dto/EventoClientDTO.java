package org.tudai.ticketya.notificaciones.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos del evento que devuelve {@code ms-eventos} y se usan para renderizar el email.
 */
@Getter
@Setter
@NoArgsConstructor
public class EventoClientDTO {
    private Long id;
    private String nombre;
    private LocalDateTime fecha;
    private LugarClientDTO lugar;
    private List<SectorClientDTO> sectores;

    /**
     * Devuelve el nombre del lugar o {@code null} si el evento no trae lugar.
     *
     * @return nombre del lugar
     */
    public String getNombreLugar() {
        return lugar != null ? lugar.getNombre() : null;
    }
}
