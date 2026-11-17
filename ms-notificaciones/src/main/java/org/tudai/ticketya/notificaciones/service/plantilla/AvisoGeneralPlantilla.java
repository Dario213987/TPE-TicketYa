package org.tudai.ticketya.notificaciones.service.plantilla;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.util.Set;

/**
 * Estrategia de plantilla para avisos generales de evento.
 * <p>
 * Cubre cancelacion, edicion, publicacion de artista y suspension. No usa detalle de
 * compra: limpia esas variables y deja que la plantilla comun muestre los datos del
 * evento y del usuario.
 */
@Component
public class AvisoGeneralPlantilla implements PlantillaNotificacion {

    private static final Set<MotivoNotificacion> MOTIVOS = Set.of(
            MotivoNotificacion.CANCELACION,
            MotivoNotificacion.EDICION,
            MotivoNotificacion.PUBLICACION_ARTISTA,
            MotivoNotificacion.SUSPENSION);

    /**
     * Devuelve los motivos generales cubiertos por esta estrategia.
     *
     * @return motivos cubiertos
     */
    @Override
    public Set<MotivoNotificacion> motivos() {
        return MOTIVOS;
    }

    /**
     * Arma el asunto con el motivo y el nombre del evento.
     *
     * @param datos datos del mensaje
     * @return asunto del aviso
     */
    @Override
    public String asunto(DatosPlantilla datos) {
        return "Notificación " + datos.motivo() + " - " + datos.nombreEvento();
    }

    /**
     * Limpia las variables de detalle porque los avisos generales no llevan compra.
     *
     * @param contexto contexto de Thymeleaf
     * @param datos    datos del mensaje
     */
    @Override
    public void completar(Context contexto, DatosPlantilla datos) {
        limpiarDetalle(contexto);
    }
}
