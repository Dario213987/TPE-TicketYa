package org.tudai.ticketya.notificaciones.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.service.plantilla.DatosPlantilla;
import org.tudai.ticketya.notificaciones.service.plantilla.PlantillaNotificacion;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PlantillaEmailService {

    private final SpringTemplateEngine templateEngine;
    private final Map<MotivoNotificacion, PlantillaNotificacion> plantillas;

    public PlantillaEmailService(SpringTemplateEngine templateEngine, List<PlantillaNotificacion> plantillas) {
        this.templateEngine = templateEngine;
        this.plantillas = registrar(plantillas);
    }

    public String asunto(NotificacionEntranteDTO entrante, EventoClientDTO evento) {
        MotivoNotificacion motivo = requerirMotivo(entrante);
        DatosPlantilla datos = new DatosPlantilla(motivo, evento, null, entrante.getDetalle());
        return plantilla(motivo).asunto(datos);
    }

    public String renderizar(NotificacionEntranteDTO entrante, EventoClientDTO evento, UsuarioClientDTO usuario) {
        MotivoNotificacion motivo = requerirMotivo(entrante);
        DatosPlantilla datos = new DatosPlantilla(motivo, evento, usuario, entrante.getDetalle());
        Context contexto = contextoComun(datos);
        plantilla(motivo).completar(contexto, datos);
        return templateEngine.process("mail/" + motivo.name(), contexto);
    }

    private Map<MotivoNotificacion, PlantillaNotificacion> registrar(List<PlantillaNotificacion> estrategias) {
        Map<MotivoNotificacion, PlantillaNotificacion> porMotivo = new EnumMap<>(MotivoNotificacion.class);
        for (PlantillaNotificacion estrategia : estrategias) {
            for (MotivoNotificacion motivo : estrategia.motivos()) {
                if (porMotivo.putIfAbsent(motivo, estrategia) != null) {
                    throw new IllegalStateException("Hay mas de una plantilla para el motivo " + motivo);
                }
            }
        }
        for (MotivoNotificacion motivo : MotivoNotificacion.values()) {
            if (!porMotivo.containsKey(motivo)) {
                throw new IllegalStateException("No hay plantilla para el motivo " + motivo);
            }
        }
        return porMotivo;
    }

    private PlantillaNotificacion plantilla(MotivoNotificacion motivo) {
        PlantillaNotificacion plantilla = plantillas.get(motivo);
        if (plantilla == null) {
            throw new IllegalArgumentException("No hay plantilla para el motivo " + motivo);
        }
        return plantilla;
    }

    private MotivoNotificacion requerirMotivo(NotificacionEntranteDTO entrante) {
        if (entrante == null || entrante.getMotivo() == null) {
            throw new IllegalArgumentException("motivo es obligatorio para armar la notificacion");
        }
        return entrante.getMotivo();
    }

    private Context contextoComun(DatosPlantilla datos) {
        Context contexto = new Context();
        EventoClientDTO evento = datos.evento();
        UsuarioClientDTO usuario = datos.usuario();

        contexto.setVariable("motivo", datos.motivo().name());
        contexto.setVariable("eventoNombre", evento != null && evento.getNombre() != null ? evento.getNombre() : "");
        contexto.setVariable("eventoFecha", evento != null && evento.getFecha() != null ? evento.getFecha().toString() : "");
        contexto.setVariable("eventoLugar", evento != null && evento.getNombreLugar() != null ? evento.getNombreLugar() : "");
        contexto.setVariable("usuarioNombre", usuario != null && usuario.getNombre() != null ? usuario.getNombre() : "");
        return contexto;
    }
}
