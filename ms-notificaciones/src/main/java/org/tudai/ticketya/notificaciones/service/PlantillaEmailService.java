package org.tudai.ticketya.notificaciones.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

@Service
public class PlantillaEmailService {

    private final SpringTemplateEngine templateEngine;

    public PlantillaEmailService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String asunto(MotivoNotificacion motivo, EventoClientDTO evento) {
        String nombreEvento = evento != null && evento.getNombre() != null ? evento.getNombre() : "evento";
        return "Notificación " + motivo + " - " + nombreEvento;
    }

    public String renderizar(MotivoNotificacion motivo, EventoClientDTO evento, UsuarioClientDTO usuario) {
        Context context = new Context();
        context.setVariable("motivo", motivo != null ? motivo.name() : "");
        context.setVariable("eventoNombre", evento != null ? evento.getNombre() : "");
        context.setVariable("eventoFecha", evento != null && evento.getFecha() != null ? evento.getFecha().toString() : "");
        context.setVariable("eventoLugar", evento != null ? evento.getNombreLugar() : "");
        context.setVariable("usuarioNombre", usuario != null ? usuario.getNombre() : "");
        return templateEngine.process("mail/" + motivo.name(), context);
    }
}
