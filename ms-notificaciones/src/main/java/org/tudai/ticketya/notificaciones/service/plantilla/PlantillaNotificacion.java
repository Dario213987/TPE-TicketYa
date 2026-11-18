package org.tudai.ticketya.notificaciones.service.plantilla;

import org.thymeleaf.context.Context;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.util.Set;

/**
 * Estrategia de plantilla para uno o mas motivos de notificacion.
 * <p>
 * Cada implementacion declara los motivos que cubre, arma el asunto y completa las
 * variables especificas del detalle. {@code PlantillaEmailService} selecciona la
 * estrategia segun el motivo y valida al arrancar que todos los motivos esten cubiertos.
 */
public interface PlantillaNotificacion {

    /**
     * Motivos que resuelve esta plantilla.
     *
     * @return conjunto de motivos cubiertos
     */
    Set<MotivoNotificacion> motivos();

    /**
     * Arma el asunto del email.
     *
     * @param datos datos comunes y detalle del mensaje
     * @return asunto renderizado
     */
    String asunto(DatosPlantilla datos);

    /**
     * Agrega al contexto de Thymeleaf las variables propias del motivo.
     *
     * @param contexto contexto de renderizado
     * @param datos    datos comunes y detalle del mensaje
     */
    void completar(Context contexto, DatosPlantilla datos);

    /**
     * Deja vacias las variables de detalle de compra para que las plantillas generales
     * no dependan de valores de un mensaje anterior.
     *
     * @param contexto contexto de renderizado
     */
    default void limpiarDetalle(Context contexto) {
        contexto.setVariable("sectorNombre", "");
        contexto.setVariable("asientosTexto", "");
        contexto.setVariable("esGeneral", false);
        contexto.setVariable("cantidad", null);
        contexto.setVariable("monto", null);
        contexto.setVariable("moneda", null);
    }
}
