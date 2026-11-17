package org.tudai.ticketya.notificaciones.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.LugarClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.SectorClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.service.plantilla.AvisoGeneralPlantilla;
import org.tudai.ticketya.notificaciones.service.plantilla.ConfirmacionCompraPlantilla;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantillaRenderizadoTest {

    private PlantillaEmailService service;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        service = new PlantillaEmailService(engine,
                List.of(new ConfirmacionCompraPlantilla(), new AvisoGeneralPlantilla()));
    }

    @Test
    void renderizaConfirmacionConFragmentoComun() {
        EventoClientDTO evento = eventoBase();
        SectorClientDTO sector = new SectorClientDTO();
        sector.setId(10L);
        sector.setNombre("Platea");
        evento.setSectores(List.of(sector));

        DetalleConfirmacionCompra detalle = new DetalleConfirmacionCompra();
        detalle.setSectorId(10L);
        detalle.setAsientos(List.of(new AsientoNotificacion(1, 2)));
        detalle.setCantidad(1);
        detalle.setMonto(new BigDecimal("1500.00"));
        detalle.setMoneda("ARS");

        String html = service.renderizar(entrante(MotivoNotificacion.CONFIRMACION_COMPRA, detalle), evento, usuarioBase());

        assertTrue(html.contains("Confirmación de compra"));
        assertTrue(html.contains("Show"));
        assertTrue(html.contains("Platea"));
        assertTrue(html.contains("1-2"));
    }

    @Test
    void renderizaCancelacionConFragmentoComun() {
        String html = service.renderizar(entrante(MotivoNotificacion.CANCELACION, null), eventoBase(), usuarioBase());

        assertTrue(html.contains("CANCELACION"));
        assertTrue(html.contains("Show"));
        assertTrue(html.contains("Ana"));
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

    private NotificacionEntranteDTO entrante(MotivoNotificacion motivo, DetalleConfirmacionCompra detalle) {
        NotificacionEntranteDTO entrante = new NotificacionEntranteDTO();
        entrante.setMensajeId("MSE-1");
        entrante.setMotivo(motivo);
        entrante.setEventoId(1L);
        entrante.setDestinatarioId(7L);
        entrante.setDetalle(detalle);
        return entrante;
    }
}
