package org.tudai.ticketya.notificaciones.service.destinatarios;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.EventoClient;
import org.tudai.ticketya.notificaciones.client.PreferenciasClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.ParticipanteClientDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PreferenciasArtistaResolver implements DestinatariosResolver {

    private static final int MAX_PARTICIPANTES = 100;
    private static final Set<MotivoNotificacion> MOTIVOS = Set.of(MotivoNotificacion.PUBLICACION_ARTISTA);

    private final EventoClient eventoClient;
    private final PreferenciasClient preferenciasClient;

    @Override
    public Set<MotivoNotificacion> motivos() {
        return MOTIVOS;
    }

    @Override
    public List<Long> resolver(NotificacionEntranteDTO entrante) {
        EventoClientDTO evento = eventoClient.obtenerEvento(entrante.getEventoId());
        String ciudad = evento != null && evento.getLugar() != null ? evento.getLugar().getCiudad() : null;
        if (ciudad == null || ciudad.isBlank()) {
            throw new IllegalStateException("El evento " + entrante.getEventoId() + " no tiene ciudad");
        }

        List<Long> participanteIds = participanteIds(evento);
        if (participanteIds.isEmpty()) {
            return List.of();
        }

        List<Long> destinatarios = new ArrayList<>();
        for (int desde = 0; desde < participanteIds.size(); desde += MAX_PARTICIPANTES) {
            int hasta = Math.min(desde + MAX_PARTICIPANTES, participanteIds.size());
            List<Long> lote = participanteIds.subList(desde, hasta);
            List<Long> encontrados = preferenciasClient.obtenerDestinatarios(ciudad, lote);
            if (encontrados != null) {
                destinatarios.addAll(encontrados);
            }
        }
        return destinatarios.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<Long> participanteIds(EventoClientDTO evento) {
        if (evento.getParticipantes() == null) {
            return List.of();
        }
        return evento.getParticipantes().stream()
                .filter(Objects::nonNull)
                .map(ParticipanteClientDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
