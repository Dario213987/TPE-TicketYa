package org.tudai.ticketya.notificaciones.service.plantilla;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.SectorClientDTO;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Estrategia de plantilla para la confirmacion de compra.
 * <p>
 * Resuelve el nombre del sector, formatea los asientos y expone cantidad, monto y
 * moneda. Si el detalle no es de confirmacion de compra, deja las variables de detalle
 * vacias y la plantilla comun se renderiza sin esos datos.
 */
@Component
public class ConfirmacionCompraPlantilla implements PlantillaNotificacion {

    /**
     * Cubre solamente el motivo {@code CONFIRMACION_COMPRA}.
     *
     * @return motivo cubierto
     */
    @Override
    public Set<MotivoNotificacion> motivos() {
        return Set.of(MotivoNotificacion.CONFIRMACION_COMPRA);
    }

    /**
     * Arma el asunto con el nombre del evento.
     *
     * @param datos datos del mensaje
     * @return asunto de confirmacion de compra
     */
    @Override
    public String asunto(DatosPlantilla datos) {
        return "Confirmación de compra - " + datos.nombreEvento();
    }

    /**
     * Completa las variables de sector, asientos, cantidad, monto y moneda.
     *
     * @param contexto contexto de Thymeleaf
     * @param datos    datos del mensaje
     */
    @Override
    public void completar(Context contexto, DatosPlantilla datos) {
        limpiarDetalle(contexto);
        if (!(datos.detalle() instanceof DetalleConfirmacionCompra detalle)) {
            return;
        }
        contexto.setVariable("sectorNombre", resolverSectorNombre(datos.evento(), detalle));
        contexto.setVariable("asientosTexto", formatearAsientos(detalle));
        contexto.setVariable("esGeneral", esGeneral(detalle));
        contexto.setVariable("cantidad", detalle.getCantidad());
        contexto.setVariable("monto", detalle.getMonto());
        contexto.setVariable("moneda", detalle.getMoneda());
    }

    /**
     * Busca el nombre del sector por id en los datos del evento; si no lo encuentra,
     * usa el id como texto.
     *
     * @param evento  datos del evento
     * @param detalle detalle de la compra
     * @return nombre o id del sector
     */
    private String resolverSectorNombre(EventoClientDTO evento, DetalleConfirmacionCompra detalle) {
        if (detalle.getSectorId() == null) {
            return "";
        }
        if (evento != null && evento.getSectores() != null) {
            for (SectorClientDTO sector : evento.getSectores()) {
                if (sector != null && Objects.equals(sector.getId(), detalle.getSectorId())) {
                    return sector.getNombre() != null ? sector.getNombre() : String.valueOf(detalle.getSectorId());
                }
            }
        }
        return String.valueOf(detalle.getSectorId());
    }

    /**
     * Formatea los asientos como {@code fila-numero} separados por coma.
     *
     * @param detalle detalle de la compra
     * @return texto de asientos o cadena vacia
     */
    private String formatearAsientos(DetalleConfirmacionCompra detalle) {
        List<AsientoNotificacion> asientos = detalle.getAsientos();
        if (asientos == null || asientos.isEmpty()) {
            return "";
        }
        return asientos.stream()
                .filter(Objects::nonNull)
                .map(a -> a.getFila() + "-" + a.getNumero())
                .collect(Collectors.joining(", "));
    }

    /**
     * Indica si la compra es de entradas generales, es decir, sin asientos numerados.
     *
     * @param detalle detalle de la compra
     * @return {@code true} si no hay asientos
     */
    private boolean esGeneral(DetalleConfirmacionCompra detalle) {
        List<AsientoNotificacion> asientos = detalle.getAsientos();
        return asientos == null || asientos.isEmpty();
    }
}
