package org.tudai.ticketya.preferencias.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaRequestDTO;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaResponseDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.config.ModelMapperConfig;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PreferenciaServiceTest {

    @Mock
    private PreferenciaRepository preferenciaRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private PreferenciaService preferenciaService;

    @Test
    void obtener_sinDocumento_devuelveListasVaciasYNoFalla() {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());

        PreferenciaResponseDTO respuesta = preferenciaService.obtener(7L);

        assertThat(respuesta.getClienteId()).isEqualTo(7L);
        assertThat(respuesta.getTiposFavoritos()).isEmpty();
        assertThat(respuesta.getArtistasFavoritos()).isEmpty();
        assertThat(respuesta.getCiudad()).isNull();
    }

    @Test
    void guardar_reemplazaElDocumentoDelClienteYNormaliza() {
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        PreferenciaResponseDTO respuesta = preferenciaService.guardar(7L, request(
                List.of(TipoEvento.RECITAL, TipoEvento.RECITAL, TipoEvento.FESTIVAL),
                List.of(15L, 15L, 20L),
                "  Tandil  "));

        ArgumentCaptor<Preferencia> captor = ArgumentCaptor.forClass(Preferencia.class);
        verify(preferenciaRepository).save(captor.capture());
        Preferencia guardada = captor.getValue();

        assertThat(guardada.getClienteId()).isEqualTo(7L);
        assertThat(guardada.getTiposFavoritos())
                .containsExactly(TipoEvento.RECITAL, TipoEvento.FESTIVAL);
        assertThat(guardada.getArtistasFavoritos()).containsExactly(15L, 20L);
        assertThat(guardada.getCiudad()).isEqualTo("Tandil");

        assertThat(respuesta.getClienteId()).isEqualTo(7L);
        assertThat(respuesta.getCiudad()).isEqualTo("Tandil");
    }

    @Test
    void guardar_listasVacias_borraLosFavoritosAnteriores() {
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        PreferenciaResponseDTO respuesta = preferenciaService.guardar(7L,
                request(List.of(), List.of(), "Tandil"));

        assertThat(respuesta.getTiposFavoritos()).isEmpty();
        assertThat(respuesta.getArtistasFavoritos()).isEmpty();
        assertThat(respuesta.getCiudad()).isEqualTo("Tandil");
    }

    @Test
    void obtener_noGuardaNadaCuandoNoHayDocumento() {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());

        preferenciaService.obtener(7L);

        verify(preferenciaRepository, never()).save(any(Preferencia.class));
    }

    private PreferenciaRequestDTO request(List<TipoEvento> tipos, List<Long> artistas, String ciudad) {
        return new PreferenciaRequestDTO()
                .tiposFavoritos(tipos)
                .artistasFavoritos(artistas)
                .ciudad(ciudad);
    }
}
