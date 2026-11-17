package org.tudai.ticketya.notificaciones.service;

import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private NotificacionEntranteDTO entrante;
    private EventoClientDTO evento;
    private UsuarioClientDTO usuario;

    @BeforeEach
    void setUp() {
        entrante = new NotificacionEntranteDTO();
        entrante.setMensajeId("MST-1-u");
        entrante.setMotivo(MotivoNotificacion.CANCELACION);
        entrante.setEventoId(10L);
        entrante.setDestinatarioId(20L);

        evento = new EventoClientDTO();
        evento.setNombre("Show");
        usuario = new UsuarioClientDTO();
        usuario.setEmail("ana@test.com");
        usuario.setNombre("Ana");
    }

    @Test
    void duplicadoEnviadoNoReenvia() {
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(notificacion(EstadoNotificacion.ENVIADO)));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(eventoClient, never()).obtenerEvento(anyLong());
    }

    @Test
    void duplicadoNoEntregableNoReenvia() {
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(notificacion(EstadoNotificacion.NO_ENTREGABLE)));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(eventoClient, never()).obtenerEvento(anyLong());
    }

    @Test
    void enviandoPrevioSeReintentaYEnvia() {
        Notificacion n = notificacion(EstadoNotificacion.ENVIANDO);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any(), any())).thenReturn("cuerpo");

        java.util.List<EstadoNotificacion> estados = new java.util.ArrayList<>();
        doAnswer(inv -> {
            estados.add(inv.getArgument(0, Notificacion.class).getEstado());
            return inv.getArgument(0);
        }).when(notificacionRepository).save(any(Notificacion.class));

        service.procesarNotificacionEntrante(entrante);

        assertThat(estados).containsExactly(EstadoNotificacion.ENVIANDO, EstadoNotificacion.ENVIADO);
        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
    }

    @Test
    void envioExitosoMarcaEnviandoLuegoEnviado() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any(), any())).thenReturn("cuerpo");

        java.util.List<EstadoNotificacion> estados = new java.util.ArrayList<>();
        doAnswer(inv -> {
            estados.add(inv.getArgument(0, Notificacion.class).getEstado());
            return inv.getArgument(0);
        }).when(notificacionRepository).save(any(Notificacion.class));

        service.procesarNotificacionEntrante(entrante);

        assertThat(estados).containsExactly(EstadoNotificacion.ENVIANDO, EstadoNotificacion.ENVIADO);
        assertThat(n.getFechaEnvio()).isNotNull();
        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
    }

    @Test
    void falloEnEnvioMarcaFallidoYRelanza() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any(), any())).thenReturn("cuerpo");
        doThrow(new RuntimeException("SMTP down")).when(emailService).enviar(any(), any(), any());

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP down");

        verify(notificacionRepository, atLeastOnce()).save(argThat(x -> x.getEstado() == EstadoNotificacion.FALLIDO));
        verify(notificacionRepository, never()).save(argThat(x -> x.getEstado() == EstadoNotificacion.ENVIADO));
    }

    @Test
    void falloEnClienteExternoMarcaFallidoYRelanza() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenThrow(new RuntimeException("evento caido"));

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(RuntimeException.class);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(notificacionRepository, atLeastOnce()).save(argThat(x -> x.getEstado() == EstadoNotificacion.FALLIDO));
    }

    @Test
    void sinEmailMarcaNoEntregableSinEnviarNiReintentar() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        usuario.setEmail(null);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(notificacionRepository).save(argThat(x -> x.getEstado() == EstadoNotificacion.NO_ENTREGABLE));
        verify(notificacionRepository, never()).save(argThat(x -> x.getEstado() == EstadoNotificacion.FALLIDO));
    }

    @Test
    void confirmacionCompraPasaDetalleYEnvia() {
        DetalleConfirmacionCompra detalle = new DetalleConfirmacionCompra();
        detalle.setSectorId(3L);
        detalle.setCantidad(2);
        entrante.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);
        entrante.setDetalleCompra(detalle);
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeId("MST-1-u")).thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(MotivoNotificacion.CONFIRMACION_COMPRA, evento, detalle)).thenReturn("asunto");
        when(plantillaEmailService.renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle)).thenReturn("cuerpo");

        service.procesarNotificacionEntrante(entrante);

        verify(plantillaEmailService).asunto(MotivoNotificacion.CONFIRMACION_COMPRA, evento, detalle);
        verify(plantillaEmailService).renderizar(MotivoNotificacion.CONFIRMACION_COMPRA, evento, usuario, detalle);
        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
    }

    @Test
    void mensajeIdObligatorio() {
        entrante.setMensajeId(" ");
        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificacionRepository, eventoClient, usuarioClient, emailService);
    }

    private Notificacion notificacion(EstadoNotificacion estado) {
        Notificacion n = new Notificacion();
        n.setId(1L);
        n.setMensajeId("MST-1-u");
        n.setEstado(estado);
        return n;
    }
}
