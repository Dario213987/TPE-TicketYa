package org.tudai.ticketya.notificaciones.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.LugarClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.SectorClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantillaEmailServiceTest {

    @Mock
    private SpringTemplateEngine templateEngine;

    private PlantillaEmailService service;

    @BeforeEach
    void setUp() {
        service = new PlantillaEmailService(templateEngine);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("html");
    }

    @Test
    void confirmacionCompraConAsientosNumeradosYMonto() {
        EventoClientDTO evento = eventoBase();
        SectorClientDTO sector = new SectorClientDTO();
        sector.setId(10L);
        sector.setNombre("Platea");
        evento.setSectores(List.of(sector));
        UsuarioClientDTO usuario = usuarioBase();
        DetalleConfirmacionCompra detalle = detalle(10L, List.of(new AsientoNotificacion(1, 2), new AsientoNotificacion(1, 3)), 2, new BigDecimal("1500.00"), "ARS");

        String asunto = service.asunto(MotivoNotificacion.CONFIRMACION_COMPRA, evento, detalle);
        service.renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle);

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/CONFIRMACION_COMPRA"), captor.capture());
        Context ctx = captor.getValue();

        assertEquals("Confirmación de compra - Show", asunto);
        assertEquals("Show", ctx.getVariable("eventoNombre"));
        assertNotNull(ctx.getVariable("eventoFecha"));
        assertEquals("Teatro", ctx.getVariable("eventoLugar"));
        assertEquals("Ana", ctx.getVariable("usuarioNombre"));
        assertEquals("Platea", ctx.getVariable("sectorNombre"));
        assertEquals("1-2, 1-3", ctx.getVariable("asientosTexto"));
        assertEquals(false, ctx.getVariable("esGeneral"));
        assertEquals(2, ctx.getVariable("cantidad"));
        assertEquals(new BigDecimal("1500.00"), ctx.getVariable("monto"));
        assertEquals("ARS", ctx.getVariable("moneda"));
    }

    @Test
    void confirmacionCompraSectorNoNumeradoMuestraGeneral() {
        EventoClientDTO evento = eventoBase();
        UsuarioClientDTO usuario = usuarioBase();
        DetalleConfirmacionCompra detalle = detalle(99L, List.of(), 3, new BigDecimal("900"), "ARS");

        service.renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle);

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/CONFIRMACION_COMPRA"), captor.capture());
        Context ctx = captor.getValue();

        assertEquals(true, ctx.getVariable("esGeneral"));
        assertEquals(3, ctx.getVariable("cantidad"));
        assertEquals("", ctx.getVariable("asientosTexto"));
        assertEquals("99", ctx.getVariable("sectorNombre"));
    }

    @Test
    void confirmacionCompraConMontoNullNoRompe() {
        EventoClientDTO evento = eventoBase();
        UsuarioClientDTO usuario = usuarioBase();
        DetalleConfirmacionCompra detalle = detalle(5L, List.of(new AsientoNotificacion(2, 1)), 1, null, null);

        assertDoesNotThrow(() -> service.renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle));

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/CONFIRMACION_COMPRA"), captor.capture());
        Context ctx = captor.getValue();
        assertNull(ctx.getVariable("monto"));
    }

    @Test
    void otroMotivoMantieneComportamientoSinDetalle() {
        EventoClientDTO evento = eventoBase();
        UsuarioClientDTO usuario = usuarioBase();

        String asunto = service.asunto(MotivoNotificacion.EDICION, evento);
        service.renderizar(MotivoNotificacion.EDICION, evento, usuario, null);

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/EDICION"), captor.capture());
        Context ctx = captor.getValue();

        assertEquals("Notificación EDICION - Show", asunto);
        assertEquals("Show", ctx.getVariable("eventoNombre"));
        assertEquals("", ctx.getVariable("sectorNombre"));
        assertEquals(false, ctx.getVariable("esGeneral"));
        assertNull(ctx.getVariable("monto"));
    }

    private EventoClientDTO eventoBase() {
        LugarClientDTO lugar = new LugarClientDTO();
        lugar.setNombre("Teatro");
        EventoClientDTO evento = new EventoClientDTO();
        evento.setId(1L);
        evento.setNombre("Show");
        evento.setFecha(LocalDateTime.of(2026, 12, 1, 20, 0));
        evento.setLugar(lugar);
        return evento;
    }

    private UsuarioClientDTO usuarioBase() {
        UsuarioClientDTO usuario = new UsuarioClientDTO();
        usuario.setId(7L);
        usuario.setNombre("Ana");
        usuario.setEmail("ana@test.com");
        return usuario;
    }

    private DetalleConfirmacionCompra detalle(Long sectorId, List<AsientoNotificacion> asientos, Integer cantidad, BigDecimal monto, String moneda) {
        DetalleConfirmacionCompra detalle = new DetalleConfirmacionCompra();
        detalle.setSectorId(sectorId);
        detalle.setAsientos(asientos);
        detalle.setCantidad(cantidad);
        detalle.setMonto(monto);
        detalle.setMoneda(moneda);
        return detalle;
    }
}
