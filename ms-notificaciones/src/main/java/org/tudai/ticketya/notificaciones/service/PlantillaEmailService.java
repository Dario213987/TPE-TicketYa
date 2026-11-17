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

/**
 * Contexto del patron Strategy para resolver la plantilla de cada notificacion.
 * <p>
 * Recibe todas las implementaciones de {@link PlantillaNotificacion} como beans y las
 * registra por motivo. Al arrancar valida que no haya duplicados y que todos los
 * motivos del contrato tengan una estrategia. Para agregar un motivo nuevo alcanza con
 * crear una estrategia y su template: este servicio no cambia.
 */
@Service
public class PlantillaEmailService {

    private final SpringTemplateEngine templateEngine;
    private final Map<MotivoNotificacion, PlantillaNotificacion> plantillas;

    /**
     * Registra las estrategias disponibles y valida la cobertura de motivos.
     *
     * @param templateEngine motor de templates Thymeleaf
     * @param plantillas     estrategias detectadas por Spring
     */
    public PlantillaEmailService(SpringTemplateEngine templateEngine, List<PlantillaNotificacion> plantillas) {
        this.templateEngine = templateEngine;
        this.plantillas = registrar(plantillas);
    }

    /**
     * Arma el asunto usando la estrategia del motivo del mensaje.
     *
     * @param entrante mensaje de notificacion
     * @param evento   datos del evento
     * @return asunto renderizado
     */
    public String asunto(NotificacionEntranteDTO entrante, EventoClientDTO evento) {
        MotivoNotificacion motivo = requerirMotivo(entrante);
        DatosPlantilla datos = new DatosPlantilla(motivo, evento, null, entrante.getDetalle());
        return plantilla(motivo).asunto(datos);
    }

    /**
     * Renderiza el HTML usando la estrategia del motivo y el template
     * {@code mail/{MOTIVO}.html}.
     *
     * @param entrante mensaje de notificacion
     * @param evento   datos del evento
     * @param usuario  datos del destinatario
     * @return cuerpo HTML renderizado
     */
    public String renderizar(NotificacionEntranteDTO entrante, EventoClientDTO evento, UsuarioClientDTO usuario) {
        MotivoNotificacion motivo = requerirMotivo(entrante);
        DatosPlantilla datos = new DatosPlantilla(motivo, evento, usuario, entrante.getDetalle());
        Context contexto = contextoComun(datos);
        plantilla(motivo).completar(contexto, datos);
        return templateEngine.process("mail/" + motivo.name(), contexto);
    }

    /**
     * Construye el registro de estrategias por motivo.
     *
     * @param estrategias estrategias disponibles
     * @return mapa inmutable logicamente por motivo
     * @throws IllegalStateException si hay duplicados o motivos sin cubrir
     */
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

    /**
     * Obtiene la estrategia registrada para un motivo.
     *
     * @param motivo motivo de la notificacion
     * @return estrategia correspondiente
     */
    private PlantillaNotificacion plantilla(MotivoNotificacion motivo) {
        PlantillaNotificacion plantilla = plantillas.get(motivo);
        if (plantilla == null) {
            throw new IllegalArgumentException("No hay plantilla para el motivo " + motivo);
        }
        return plantilla;
    }

    /**
     * Valida que el mensaje tenga motivo y lo devuelve.
     *
     * @param entrante mensaje de notificacion
     * @return motivo del mensaje
     */
    private MotivoNotificacion requerirMotivo(NotificacionEntranteDTO entrante) {
        if (entrante == null || entrante.getMotivo() == null) {
            throw new IllegalArgumentException("motivo es obligatorio para armar la notificacion");
        }
        return entrante.getMotivo();
    }

    /**
     * Carga las variables comunes que comparten todos los templates.
     *
     * @param datos datos del mensaje
     * @return contexto de Thymeleaf con variables comunes
     */
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
