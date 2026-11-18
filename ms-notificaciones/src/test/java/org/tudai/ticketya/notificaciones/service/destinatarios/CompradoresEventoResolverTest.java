package org.tudai.ticketya.notificaciones.service.destinatarios;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.TicketsClient;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompradoresEventoResolverTest {

    @Mock
    private TicketsClient ticketsClient;

    @InjectMocks
    private CompradoresEventoResolver resolver;

    @Test
    void motivosCubiertos() {
        assertThat(resolver.motivos()).containsExactlyInAnyOrder(
                MotivoNotificacion.CANCELACION,
                MotivoNotificacion.EDICION,
                MotivoNotificacion.SUSPENSION);
    }

    @Test
    void resuelveCompradoresDistintosYNoNulos() {
        when(ticketsClient.obtenerCompradores(10L)).thenReturn(Arrays.asList(21L, 20L, 20L, null));

        assertThat(resolver.resolver(entrante(MotivoNotificacion.CANCELACION))).containsExactly(21L, 20L);
        verify(ticketsClient).obtenerCompradores(10L);
    }

    @Test
    void listaVaciaONulaNoGeneraDestinatarios() {
        when(ticketsClient.obtenerCompradores(10L)).thenReturn(List.of());
        when(ticketsClient.obtenerCompradores(11L)).thenReturn(null);

        assertThat(resolver.resolver(entrante(MotivoNotificacion.EDICION, 10L))).isEmpty();
        assertThat(resolver.resolver(entrante(MotivoNotificacion.SUSPENSION, 11L))).isEmpty();
    }

    private NotificacionEntranteDTO entrante(MotivoNotificacion motivo) {
        return entrante(motivo, 10L);
    }

    private NotificacionEntranteDTO entrante(MotivoNotificacion motivo, Long eventoId) {
        NotificacionEntranteDTO entrante = new NotificacionEntranteDTO();
        entrante.setMensajeId("MSE-1-" + motivo);
        entrante.setMotivo(motivo);
        entrante.setEventoId(eventoId);
        return entrante;
    }
}
