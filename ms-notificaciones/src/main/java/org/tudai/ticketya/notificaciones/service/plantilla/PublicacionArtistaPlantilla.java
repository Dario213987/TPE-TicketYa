package org.tudai.ticketya.notificaciones.service.plantilla;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.ParticipanteClientDTO;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class PublicacionArtistaPlantilla implements PlantillaNotificacion {

    private static final Set<MotivoNotificacion> MOTIVOS = Set.of(MotivoNotificacion.PUBLICACION_ARTISTA);

    @Override
    public Set<MotivoNotificacion> motivos() {
        return MOTIVOS;
    }

    @Override
    public String asunto(DatosPlantilla datos) {
        return "Notificación " + datos.motivo() + " - " + datos.nombreEvento();
    }

    @Override
    public void completar(Context contexto, DatosPlantilla datos) {
        limpiarDetalle(contexto);
        EventoClientDTO evento = datos.evento();
        String ciudad = "";
        List<String> participantes = List.of();
        if (evento != null) {
            if (evento.getLugar() != null && evento.getLugar().getCiudad() != null) {
                ciudad = evento.getLugar().getCiudad();
            }
            if (evento.getParticipantes() != null) {
                participantes = evento.getParticipantes().stream()
                        .filter(Objects::nonNull)
                        .map(ParticipanteClientDTO::getNombre)
                        .filter(nombre -> nombre != null && !nombre.isBlank())
                        .toList();
            }
        }
        contexto.setVariable("eventoCiudad", ciudad);
        contexto.setVariable("participantesTexto", String.join(", ", participantes));
    }
}
