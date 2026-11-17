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

@Component
public class ConfirmacionCompraPlantilla implements PlantillaNotificacion {

    @Override
    public Set<MotivoNotificacion> motivos() {
        return Set.of(MotivoNotificacion.CONFIRMACION_COMPRA);
    }

    @Override
    public String asunto(DatosPlantilla datos) {
        return "Confirmación de compra - " + datos.nombreEvento();
    }

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

    private boolean esGeneral(DetalleConfirmacionCompra detalle) {
        List<AsientoNotificacion> asientos = detalle.getAsientos();
        return asientos == null || asientos.isEmpty();
    }
}
