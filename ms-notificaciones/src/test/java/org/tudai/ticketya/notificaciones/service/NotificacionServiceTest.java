package org.tudai.ticketya.notificaciones.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.EventoClient;
import org.tudai.ticketya.notificaciones.client.UsuarioClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.notificaciones.domain.Notificacion;
import org.tudai.ticketya.notificaciones.repository.NotificacionRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock private EventoClient eventoClient;
    @Mock private UsuarioClient usuarioClient;
    @Mock private PlantillaEmailService plantillaEmailService;
    @Mock private EmailService emailService;
    @Mock private NotificacionRepository notificacionRepository;

    @InjectMocks
    private NotificacionService service;

    @Test
    void duplicadoEnviadoNoReenvia() {
        NotificacionEntranteDTO entrante = entrante(MotivoNotificacion.CANCELACION, null);
        Notificacion existente = notificacion(EstadoNotificacion.ENVIADO);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(existente));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(eventoClient, never()).obtenerEvento(anyLong());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void duplicadoEnviandoNoReenvia() {
        NotificacionEntranteDTO entrante = entrante(MotivoNotificacion.CANCELACION, null);
        Notificacion existente = notificacion(EstadoNotificacion.ENVIANDO);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(existente));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
    }

    @Test
    void confirmacionCompraPasaDetalleYEnvia() {
        DetalleConfirmacionCompra detalle = new DetalleConfirmacionCompra();
        detalle.setSectorId(3L);
        detalle.setCantidad(2);
        NotificacionEntranteDTO entrante = entrante(MotivoNotificacion.CONFIRMACION_COMPRA, detalle);
        Notificacion pendiente = notificacion(EstadoNotificacion.PENDIENTE);
        EventoClientDTO evento = new EventoClientDTO();
        evento.setNombre("Show");
        UsuarioClientDTO usuario = new UsuarioClientDTO();
        usuario.setEmail("ana@test.com");

        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(pendiente));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(MotivoNotificacion.CONFIRMACION_COMPRA, evento, detalle)).thenReturn("asunto");
        when(plantillaEmailService.renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle)).thenReturn("cuerpo");

        service.procesarNotificacionEntrante(entrante);

        verify(plantillaEmailService).asunto(MotivoNotificacion.CONFIRMACION_COMPRA, evento, detalle);
        verify(plantillaEmailService).renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle);
        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
    }

    private NotificacionEntranteDTO entrante(MotivoNotificacion motivo, DetalleConfirmacionCompra detalle) {
        NotificacionEntranteDTO dto = new NotificacionEntranteDTO();
        dto.setMensajeId("MST-1-u");
        dto.setMotivo(motivo);
        dto.setEventoId(10L);
        dto.setDestinatarioId(20L);
        dto.setDetalleCompra(detalle);
        return dto;
    }

    private Notificacion notificacion(EstadoNotificacion estado) {
        Notificacion n = new Notificacion();
        n.setId(1L);
        n.setMensajeId("MST-1-u");
        n.setEstado(estado);
        return n;
    }
}
