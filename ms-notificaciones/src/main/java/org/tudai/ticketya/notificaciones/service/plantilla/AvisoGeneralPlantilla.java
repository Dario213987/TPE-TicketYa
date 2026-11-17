package org.tudai.ticketya.notificaciones.service.plantilla;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.util.Set;

@Component
public class AvisoGeneralPlantilla implements PlantillaNotificacion {

    private static final Set<MotivoNotificacion> MOTIVOS = Set.of(
            MotivoNotificacion.CANCELACION,
            MotivoNotificacion.EDICION,
            MotivoNotificacion.PUBLICACION_ARTISTA,
            MotivoNotificacion.SUSPENSION);

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
    }
}
