package org.tudai.ticketya.notificaciones.service.plantilla;

import org.thymeleaf.context.Context;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.util.Set;

public interface PlantillaNotificacion {

    Set<MotivoNotificacion> motivos();

    String asunto(DatosPlantilla datos);

    void completar(Context contexto, DatosPlantilla datos);

    default void limpiarDetalle(Context contexto) {
        contexto.setVariable("sectorNombre", "");
        contexto.setVariable("asientosTexto", "");
        contexto.setVariable("esGeneral", false);
        contexto.setVariable("cantidad", null);
        contexto.setVariable("monto", null);
        contexto.setVariable("moneda", null);
    }
}
