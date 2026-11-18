package org.tudai.ticketya.preferencias.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DestinatariosServiceTest {

    @Mock
    private PreferenciaRepository preferenciaRepository;

    @InjectMocks
    private DestinatariosService destinatariosService;

    @Test
    void obtenerDestinatarios_participanteYCiudadCoincidentes_devuelveElUsuario() {
        given(preferenciaRepository.findByArtistasFavoritosIn(Set.of(15L)))
                .willReturn(List.of(preferencia(7L, "Tandil", List.of(15L))));

        List<Long> respuesta = destinatariosService.obtenerDestinatarios(List.of(15L), "TÁNDIL");

        assertThat(respuesta).containsExactly(7L);
    }

    @Test
    void obtenerDestinatarios_participanteCoincidenteOtraCiudad_noDevuelve() {
        given(preferenciaRepository.findByArtistasFavoritosIn(Set.of(15L)))
                .willReturn(List.of(preferencia(7L, "Rauch", List.of(15L))));

        List<Long> respuesta = destinatariosService.obtenerDestinatarios(List.of(15L), "Tandil");

        assertThat(respuesta).isEmpty();
    }

    @Test
    void obtenerDestinatarios_ciudadCoincidenteSinElParticipante_noDevuelve() {
        given(preferenciaRepository.findByArtistasFavoritosIn(Set.of(15L)))
                .willReturn(List.of(preferencia(7L, "Tandil", List.of(99L))));

        List<Long> respuesta = destinatariosService.obtenerDestinatarios(List.of(15L), "Tandil");

        assertThat(respuesta).isEmpty();
    }

    @Test
    void obtenerDestinatarios_dosParticipantesCoincidentes_devuelveElUsuarioUnaSolaVez() {
        Preferencia preferencia = preferencia(7L, "Tandil", List.of(15L, 20L));
        given(preferenciaRepository.findByArtistasFavoritosIn(Set.of(15L, 20L)))
                .willReturn(List.of(preferencia, preferencia));

        List<Long> respuesta = destinatariosService.obtenerDestinatarios(List.of(15L, 20L), "Tandil");

        assertThat(respuesta).containsExactly(7L);
    }

    @Test
    void obtenerDestinatarios_listaVacia_devuelveVaciaSinConsultar() {
        assertThat(destinatariosService.obtenerDestinatarios(List.of(), "Tandil")).isEmpty();
        assertThat(destinatariosService.obtenerDestinatarios(null, "Tandil")).isEmpty();

        verifyNoInteractions(preferenciaRepository);
    }

    @Test
    void obtenerDestinatarios_idsRepetidosYConNulos_consultaConElSetFiltrado() {
        Preferencia siete = preferencia(7L, "Tandil", List.of(15L));
        Preferencia doce = preferencia(12L, "Tándil", List.of(20L));
        given(preferenciaRepository.findByArtistasFavoritosIn(Set.of(15L, 20L)))
                .willReturn(List.of(doce, siete));

        List<Long> respuesta = destinatariosService.obtenerDestinatarios(
                Arrays.asList(15L, 15L, null, 20L), "Tandil");

        assertThat(respuesta).containsExactly(7L, 12L);
        verify(preferenciaRepository).findByArtistasFavoritosIn(Set.of(15L, 20L));
    }

    @Test
    void obtenerDestinatarios_ciudadVacia_devuelveVaciaSinConsultar() {
        assertThat(destinatariosService.obtenerDestinatarios(List.of(15L), "   ")).isEmpty();

        verifyNoInteractions(preferenciaRepository);
    }

    private static Preferencia preferencia(Long clienteId, String ciudad, List<Long> artistas) {
        return Preferencia.builder()
                .clienteId(clienteId)
                .ciudad(ciudad)
                .artistasFavoritos(artistas)
                .build();
    }
}
