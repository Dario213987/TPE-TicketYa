package org.tudai.ticketya.notificaciones.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.SectorClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PlantillaEmailService {

    private final SpringTemplateEngine templateEngine;

    public PlantillaEmailService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String asunto(MotivoNotificacion motivo, EventoClientDTO evento) {
        return asunto(motivo, evento, null);
    }

    /**
     * Genera el asunto del email. Para CONFIRMACION_COMPRA usa "Confirmación de compra - {evento}",
     * para el resto "Notificación {motivo} - {evento}" (con fallback "evento" si falta nombre).
     */
    public String asunto(MotivoNotificacion motivo, EventoClientDTO evento, DetalleConfirmacionCompra detalle) {
        String nombreEvento = evento != null && evento.getNombre() != null ? evento.getNombre() : "evento";

        if (motivo == MotivoNotificacion.CONFIRMACION_COMPRA) {
            return "Confirmación de compra - " + nombreEvento;

        }
        return "Notificación " + motivo + " - " + nombreEvento;
    }

    public String renderizar(MotivoNotificacion motivo, EventoClientDTO evento, UsuarioClientDTO usuario) {
        return renderizar(motivo, evento, usuario, null);
    }

    /**
     * Renderiza mail/{motivo}.html con contexto de evento/usuario (enriquecidos por Feign) y,
     * si es CONFIRMACION_COMPRA con detalle, agrega sector resuelto, asientos, cantidad y monto/moneda.
     * Sector no numerado: asientos vacíos => esGeneral=true (plantilla muestra "General x N").
     * Monto/moneda pueden ser null y se exponen tal cual para que la plantilla los tolere.
     */
    public String renderizar(MotivoNotificacion motivo, EventoClientDTO evento, UsuarioClientDTO usuario, DetalleConfirmacionCompra detalle) {
        Context context = new Context();
        context.setVariable("motivo", motivo != null ? motivo.name() : "");
        context.setVariable("eventoNombre", evento != null && evento.getNombre() != null ? evento.getNombre() : "");
        context.setVariable("eventoFecha", evento != null && evento.getFecha() != null ? evento.getFecha().toString() : "");
        context.setVariable("eventoLugar", evento != null && evento.getNombreLugar() != null ? evento.getNombreLugar() : "");
        context.setVariable("usuarioNombre", usuario != null && usuario.getNombre() != null ? usuario.getNombre() : "");

        if (motivo == MotivoNotificacion.CONFIRMACION_COMPRA && detalle != null) {
            context.setVariable("sectorNombre", resolverSectorNombre(evento, detalle));
            context.setVariable("asientosTexto", formatearAsientos(detalle));
            context.setVariable("esGeneral", esGeneral(detalle));
            context.setVariable("cantidad", detalle.getCantidad());
            context.setVariable("monto", detalle.getMonto());
            context.setVariable("moneda", detalle.getMoneda());

        } else {
            context.setVariable("sectorNombre", "");
            context.setVariable("asientosTexto", "");
            context.setVariable("esGeneral", false);
            context.setVariable("cantidad", null);
            context.setVariable("monto", null);
            context.setVariable("moneda", null);
        }

        return templateEngine.process("mail/" + (motivo != null ? motivo.name() : ""), context);
    }

    /**
     * Resuelve el nombre del sector cruzando detalle.sectorId con evento.sectores; si no hay match
     * o faltan datos devuelve el sectorId como string (o vacío si es null).
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
     * Formatea asientos numerados como "fila-numero, ..." (omitiendo nulos); lista vacía/null => "".
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
     * Considera sector general/no numerado cuando no hay asientos específicos en el detalle.
     */
    private boolean esGeneral(DetalleConfirmacionCompra detalle) {
        List<AsientoNotificacion> asientos = detalle.getAsientos();
        return asientos == null || asientos.isEmpty();
    }
}
