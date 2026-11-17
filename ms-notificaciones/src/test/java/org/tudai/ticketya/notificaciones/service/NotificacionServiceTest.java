package org.tudai.ticketya.notificaciones.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tudai.ticketya.contract.notificaciones.messaging.DetalleConfirmacionCompra;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.EventoClient;
import org.tudai.ticketya.notificaciones.client.TicketsClient;
import org.tudai.ticketya.notificaciones.client.UsuarioClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.notificaciones.domain.Notificacion;
import org.tudai.ticketya.notificaciones.repository.NotificacionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock private EventoClient eventoClient;
    @Mock private UsuarioClient usuarioClient;
    @Mock private TicketsClient ticketsClient;
    @Mock private PlantillaEmailService plantillaEmailService;
    @Mock private EmailService emailService;
    @Mock private NotificacionRepository notificacionRepository;

    @Spy
    private TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

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
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(notificacion(EstadoNotificacion.ENVIADO)));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(eventoClient, never()).obtenerEvento(anyLong());
    }

    @Test
    void duplicadoNoEntregableNoReenvia() {
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(notificacion(EstadoNotificacion.NO_ENTREGABLE)));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(eventoClient, never()).obtenerEvento(anyLong());
    }

    @Test
    void enviandoPrevioNoSeReenvia() {
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(notificacion(EstadoNotificacion.ENVIANDO)));

        service.procesarNotificacionEntrante(entrante);

        verify(emailService, never()).enviar(any(), any(), any());
        verify(eventoClient, never()).obtenerEvento(anyLong());
    }

    @Test
    void fallidoPrevioSeReintentaYEnvia() {
        Notificacion n = notificacion(EstadoNotificacion.FALLIDO);
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any())).thenReturn("cuerpo");

        service.procesarNotificacionEntrante(entrante);

        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
    }

    @Test
    void envioExitosoMarcaEnviandoLuegoEnviado() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any())).thenReturn("cuerpo");

        List<EstadoNotificacion> estados = new ArrayList<>();
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
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any())).thenReturn("cuerpo");
        doThrow(new RuntimeException("SMTP down")).when(emailService).enviar(any(), any(), any());

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP down");

        verify(notificacionRepository, atLeastOnce()).save(argThat(x -> x.getEstado() == EstadoNotificacion.FALLIDO));
        verify(notificacionRepository, never()).save(argThat(x -> x.getEstado() == EstadoNotificacion.ENVIADO));
    }

    @Test
    void falloAlMarcarEnviadoNoMarcaFallidoNiReenvia() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any())).thenReturn("cuerpo");
        doAnswer(inv -> {
            Notificacion guardada = inv.getArgument(0, Notificacion.class);
            if (guardada.getEstado() == EstadoNotificacion.ENVIADO) {
                throw new RuntimeException("db caida");
            }
            return guardada;
        }).when(notificacionRepository).save(any(Notificacion.class));

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db caida");

        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
        verify(notificacionRepository, never())
                .save(argThat(x -> x.getEstado() == EstadoNotificacion.FALLIDO));
    }

    @Test
    void falloEnClienteExternoMarcaFallidoYRelanza() {
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
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
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
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
        entrante.setDetalle(detalle);
        Notificacion n = notificacion(EstadoNotificacion.PENDIENTE);
        when(notificacionRepository.findByMensajeIdAndDestinatarioId("MST-1-u", 20L))
                .thenReturn(Optional.of(n));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario);
        when(plantillaEmailService.asunto(entrante, evento)).thenReturn("asunto");
        when(plantillaEmailService.renderizar(entrante, evento, usuario)).thenReturn("cuerpo");

        service.procesarNotificacionEntrante(entrante);

        assertThat(entrante.getDetalle()).isSameAs(detalle);
        verify(plantillaEmailService).asunto(entrante, evento);
        verify(plantillaEmailService).renderizar(entrante, evento, usuario);
        verify(emailService).enviar("ana@test.com", "asunto", "cuerpo");
    }

    @Test
    void mensajeIdObligatorio() {
        entrante.setMensajeId(" ");

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificacionRepository, eventoClient, usuarioClient, ticketsClient,
                plantillaEmailService, emailService);
    }

    @Test
    void confirmacionDeCompraSinDestinatarioNoSeTrataComoAvisoMasivo() {
        entrante.setMotivo(MotivoNotificacion.CONFIRMACION_COMPRA);
        entrante.setDestinatarioId(null);

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONFIRMACION_COMPRA requiere destinatarioId");
        verifyNoInteractions(notificacionRepository, eventoClient, usuarioClient, ticketsClient,
                plantillaEmailService, emailService);
    }

    @Test
    void avisoMasivoConDosCompradoresGeneraDosNotificacionesYEnviaDosMails() {
        prepararAvisoMasivo(List.of(20L, 21L));
        prepararPersistenciaMasiva();
        List<String> estados = capturarEstados();
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario("ana@test.com"));
        when(usuarioClient.obtenerUsuario(21L)).thenReturn(usuario("bruno@test.com"));

        service.procesarNotificacionEntrante(entrante);

        verify(ticketsClient).obtenerCompradores(10L);
        ArgumentCaptor<Iterable<Notificacion>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificacionRepository).saveAllAndFlush(captor.capture());
        List<Notificacion> creadas = new ArrayList<>();
        captor.getValue().forEach(creadas::add);
        assertThat(creadas).extracting(Notificacion::getDestinatarioId)
                .containsExactlyInAnyOrder(20L, 21L);
        verify(emailService).enviar(eq("ana@test.com"), any(), any());
        verify(emailService).enviar(eq("bruno@test.com"), any(), any());
        assertThat(estados).containsExactly(
                "20:" + EstadoNotificacion.ENVIANDO,
                "20:" + EstadoNotificacion.ENVIADO,
                "21:" + EstadoNotificacion.ENVIANDO,
                "21:" + EstadoNotificacion.ENVIADO);
    }

    @Test
    void avisoMasivoSinCompradoresNoEnviaNiPersiste() {
        entrante.setDestinatarioId(null);
        when(notificacionRepository.findByMensajeIdOrderByIdAsc("MST-1-u")).thenReturn(List.of());
        when(ticketsClient.obtenerCompradores(10L)).thenReturn(List.of());

        service.procesarNotificacionEntrante(entrante);

        verify(ticketsClient).obtenerCompradores(10L);
        verify(notificacionRepository, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(emailService);
    }

    @Test
    void avisoMasivoConMsTicketsCaidoNoCreaFilasNiEnviaMails() {
        entrante.setDestinatarioId(null);
        when(notificacionRepository.findByMensajeIdOrderByIdAsc("MST-1-u")).thenReturn(List.of());
        when(ticketsClient.obtenerCompradores(10L)).thenThrow(new RuntimeException("tickets caido"));

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("tickets caido");

        verify(notificacionRepository, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(emailService);
    }

    @Test
    void avisoMasivoReutilizaFilasExistentesSinConsultarTickets() {
        entrante.setDestinatarioId(null);
        Notificacion pendiente = notificacion(1L, 20L, EstadoNotificacion.PENDIENTE);
        Notificacion enviada = notificacion(2L, 21L, EstadoNotificacion.ENVIADO);
        when(notificacionRepository.findByMensajeIdOrderByIdAsc("MST-1-u"))
                .thenReturn(List.of(pendiente, enviada));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario("ana@test.com"));
        when(plantillaEmailService.asunto(any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any())).thenReturn("cuerpo");

        service.procesarNotificacionEntrante(entrante);

        verifyNoInteractions(ticketsClient);
        verify(emailService).enviar(eq("ana@test.com"), any(), any());
        verify(emailService, never()).enviar(eq("bruno@test.com"), any(), any());
    }

    @Test
    void avisoMasivoConUnFalloContinuaConLosDemasYRelanza() {
        prepararAvisoMasivo(List.of(20L, 21L));
        prepararPersistenciaMasiva();
        List<String> estados = capturarEstados();
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario("ana@test.com"));
        when(usuarioClient.obtenerUsuario(21L)).thenReturn(usuario("bruno@test.com"));
        doAnswer(inv -> {
            if ("bruno@test.com".equals(inv.getArgument(0, String.class))) {
                throw new RuntimeException("SMTP down");
            }
            return null;
        }).when(emailService).enviar(any(), any(), any());

        assertThatThrownBy(() -> service.procesarNotificacionEntrante(entrante))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fallo el envio de 1 notificaciones");

        verify(emailService).enviar(eq("ana@test.com"), any(), any());
        verify(emailService).enviar(eq("bruno@test.com"), any(), any());
        assertThat(estados).containsExactly(
                "20:" + EstadoNotificacion.ENVIANDO,
                "20:" + EstadoNotificacion.ENVIADO,
                "21:" + EstadoNotificacion.ENVIANDO,
                "21:" + EstadoNotificacion.FALLIDO);
    }

    @Test
    void avisoMasivoDuplicadoNoReenvia() {
        prepararAvisoMasivo(List.of(20L, 21L));
        prepararPersistenciaMasiva();
        when(usuarioClient.obtenerUsuario(20L)).thenReturn(usuario("ana@test.com"));
        when(usuarioClient.obtenerUsuario(21L)).thenReturn(usuario("bruno@test.com"));

        service.procesarNotificacionEntrante(entrante);
        verify(emailService, times(2)).enviar(any(), any(), any());
        clearInvocations(emailService, ticketsClient);

        when(notificacionRepository.findByMensajeIdOrderByIdAsc("MST-1-u"))
                .thenReturn(List.of(notificacion(1L, 20L, EstadoNotificacion.ENVIADO),
                        notificacion(2L, 21L, EstadoNotificacion.ENVIADO)));

        service.procesarNotificacionEntrante(entrante);

        verifyNoInteractions(emailService, ticketsClient);
        verify(notificacionRepository, times(1)).saveAllAndFlush(anyList());
    }

    private void prepararAvisoMasivo(List<Long> compradores) {
        entrante.setMensajeId("MST-1-u");
        entrante.setDestinatarioId(null);
        when(notificacionRepository.findByMensajeIdOrderByIdAsc("MST-1-u")).thenReturn(List.of());
        when(ticketsClient.obtenerCompradores(10L)).thenReturn(compradores);
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento);
        when(plantillaEmailService.asunto(any(), any())).thenReturn("asunto");
        when(plantillaEmailService.renderizar(any(), any(), any())).thenReturn("cuerpo");
    }

    private List<String> capturarEstados() {
        List<String> estados = new ArrayList<>();
        doAnswer(inv -> {
            Notificacion notificacion = inv.getArgument(0);
            estados.add(notificacion.getDestinatarioId() + ":" + notificacion.getEstado());
            return notificacion;
        }).when(notificacionRepository).save(any(Notificacion.class));
        return estados;
    }

    private void prepararPersistenciaMasiva() {
        List<Notificacion> guardadas = new ArrayList<>();
        doAnswer(inv -> {
            List<Notificacion> lote = inv.getArgument(0);
            for (Notificacion notificacion : lote) {
                notificacion.setId((long) (guardadas.size() + 1));
                guardadas.add(notificacion);
            }
            return lote;
        }).when(notificacionRepository).saveAllAndFlush(anyList());
        when(notificacionRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return guardadas.stream().filter(n -> id.equals(n.getId())).findFirst();
        });
    }

    private UsuarioClientDTO usuario(String email) {
        UsuarioClientDTO dto = new UsuarioClientDTO();
        dto.setEmail(email);
        dto.setNombre("Destinatario");
        return dto;
    }

    private Notificacion notificacion(EstadoNotificacion estado) {
        return notificacion(1L, 20L, estado);
    }

    private Notificacion notificacion(Long id, long destinatarioId, EstadoNotificacion estado) {
        Notificacion n = new Notificacion();
        n.setId(id);
        n.setMensajeId("MST-1-u");
        n.setDestinatarioId(destinatarioId);
        n.setEstado(estado);
        return n;
    }
}
