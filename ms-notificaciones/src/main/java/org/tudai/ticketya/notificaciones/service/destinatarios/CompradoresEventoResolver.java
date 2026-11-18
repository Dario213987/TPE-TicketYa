package org.tudai.ticketya.notificaciones.service.destinatarios;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.TicketsClient;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CompradoresEventoResolver implements DestinatariosResolver {

    private static final Set<MotivoNotificacion> MOTIVOS = Set.of(
            MotivoNotificacion.CANCELACION,
            MotivoNotificacion.EDICION,
            MotivoNotificacion.SUSPENSION);

    private final TicketsClient ticketsClient;

    @Override
    public Set<MotivoNotificacion> motivos() {
        return MOTIVOS;
    }

    @Override
    public List<Long> resolver(NotificacionEntranteDTO entrante) {
        List<Long> compradores = ticketsClient.obtenerCompradores(entrante.getEventoId());
        if (compradores == null || compradores.isEmpty()) {
            return List.of();
        }
        return compradores.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
