package org.tudai.ticketya.notificaciones.service.destinatarios;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.EventoClient;
import org.tudai.ticketya.notificaciones.client.PreferenciasClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.LugarClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.ParticipanteClientDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciasArtistaResolverTest {

    @Mock
    private EventoClient eventoClient;

    @Mock
    private PreferenciasClient preferenciasClient;

    @InjectMocks
    private PreferenciasArtistaResolver resolver;

    private NotificacionEntranteDTO entrante;

    @BeforeEach
    void setUp() {
        entrante = new NotificacionEntranteDTO();
        entrante.setMensajeId("MSE-PUBLICACION_ARTISTA-10");
        entrante.setMotivo(MotivoNotificacion.PUBLICACION_ARTISTA);
        entrante.setEventoId(10L);
    }

    @Test
    void resuelvePorParticipantesFavoritosYCiudad() {
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento("Tandil", List.of(participante(15L), participante(16L))));
        when(preferenciasClient.obtenerDestinatarios("Tandil", List.of(15L, 16L)))
                .thenReturn(Arrays.asList(30L, 20L, 20L, null));

        List<Long> destinatarios = resolver.resolver(entrante);

        assertThat(destinatarios).containsExactly(20L, 30L);
        verify(preferenciasClient).obtenerDestinatarios("Tandil", List.of(15L, 16L));
    }

    @Test
    void clienteQueNoCumpleAmbosCriteriosNoSeDevuelve() {
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento("Tandil", List.of(participante(15L))));
        when(preferenciasClient.obtenerDestinatarios("Tandil", List.of(15L))).thenReturn(List.of());

        assertThat(resolver.resolver(entrante)).isEmpty();
    }

    @Test
    void sinParticipantesNoConsultaPreferencias() {
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento("Tandil", List.of()));

        assertThat(resolver.resolver(entrante)).isEmpty();
        verifyNoInteractions(preferenciasClient);
    }

    @Test
    void fragmentaParticipantesEnLotesDeCien() {
        List<ParticipanteClientDTO> participantes = new ArrayList<>();
        for (long id = 1; id <= 101; id++) {
            participantes.add(participante(id));
        }
        List<Long> primerLote = LongStream.rangeClosed(1, 100).boxed().toList();
        List<Long> segundoLote = LongStream.rangeClosed(101, 101).boxed().toList();
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento("Tandil", participantes));
        when(preferenciasClient.obtenerDestinatarios("Tandil", primerLote)).thenReturn(List.of(20L));
        when(preferenciasClient.obtenerDestinatarios("Tandil", segundoLote)).thenReturn(List.of(30L));

        assertThat(resolver.resolver(entrante)).containsExactly(20L, 30L);
        verify(preferenciasClient).obtenerDestinatarios(eq("Tandil"), eq(primerLote));
        verify(preferenciasClient).obtenerDestinatarios(eq("Tandil"), eq(segundoLote));
    }

    @Test
    void falloDePreferenciasSePropaga() {
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento("Tandil", List.of(participante(15L))));
        when(preferenciasClient.obtenerDestinatarios(anyString(), anyList()))
                .thenThrow(new RuntimeException("preferencias caido"));

        assertThatThrownBy(() -> resolver.resolver(entrante))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("preferencias caido");
    }

    @Test
    void ciudadAusenteFallaAntesDeConsultarPreferencias() {
        when(eventoClient.obtenerEvento(10L)).thenReturn(evento(null, List.of(participante(15L))));

        assertThatThrownBy(() -> resolver.resolver(entrante))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene ciudad");
        verify(preferenciasClient, never()).obtenerDestinatarios(anyString(), anyList());
    }

    private EventoClientDTO evento(String ciudad, List<ParticipanteClientDTO> participantes) {
        LugarClientDTO lugar = new LugarClientDTO();
        lugar.setCiudad(ciudad);
        EventoClientDTO evento = new EventoClientDTO();
        evento.setLugar(lugar);
        evento.setParticipantes(participantes);
        return evento;
    }

    private ParticipanteClientDTO participante(Long id) {
        ParticipanteClientDTO participante = new ParticipanteClientDTO();
        participante.setId(id);
        participante.setNombre("Artista " + id);
        return participante;
    }
}
