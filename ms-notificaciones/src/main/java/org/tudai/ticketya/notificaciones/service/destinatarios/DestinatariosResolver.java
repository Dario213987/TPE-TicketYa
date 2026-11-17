package org.tudai.ticketya.notificaciones.service.destinatarios;

import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.util.List;
import java.util.Set;

public interface DestinatariosResolver {

    Set<MotivoNotificacion> motivos();

    List<Long> resolver(NotificacionEntranteDTO entrante);
}
