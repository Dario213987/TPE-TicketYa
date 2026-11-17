package org.tudai.ticketya.preferencias.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tudai.ticketya.contract.eventos.api.EventosApiClient;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoResumen;
import org.tudai.ticketya.contract.eventos.model.PagedEventos;
import org.tudai.ticketya.contract.eventos.model.PagedLugaresPage;
import org.tudai.ticketya.contract.preferencias.model.RecomendacionResponseDTO;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.preferencias.domain.EventoCandidato;
import org.tudai.ticketya.preferencias.domain.MotivoRecomendacion;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.domain.Recomendacion;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecomendacionEndpointServiceTest {

    @Mock
    private EventosApiClient eventosClient;

    @Mock
    private RecomendacionService recomendacionService;

    @Mock
    private PreferenciaRepository preferenciaRepository;

    @InjectMocks
    private RecomendacionEndpointService endpointService;

    @Test
    void recomendar_mapeaElEventoYElMotivoDelScorer() {
        dadoPagina(resumen(5L, "Recital", "RECITAL", "Tandil", EstadoEvento.PUBLICADO, 15L));
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(preferenciaConFavoritos()));
        given(recomendacionService.recomendar(eq(7L), anyList(), eq(10)))
                .willReturn(List.of(new Recomendacion(5L, 5, MotivoRecomendacion.ARTISTA)));

        List<RecomendacionResponseDTO> resultado = endpointService.recomendar(7L);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.getEventoId()).isEqualTo(5L);
            assertThat(recomendacion.getNombre()).isEqualTo("Recital");
            assertThat(recomendacion.getTipo().name()).isEqualTo("RECITAL");
            assertThat(recomendacion.getCiudad()).isEqualTo("Tandil");
            assertThat(recomendacion.getEstado()).isEqualTo("PUBLICADO");
            assertThat(recomendacion.getMotivo())
                    .isEqualTo(org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion.ARTISTA);
            assertThat(recomendacion.getPuntaje()).isEqualTo(5);
        });
    }

    @Test
    void recomendar_filtraLosEventosAgotadosYLosMandaSoloPublicadosAlScorer() {
        dadoPagina(
                resumen(5L, "Publicado", "RECITAL", "Tandil", EstadoEvento.PUBLICADO, 15L),
                resumen(6L, "Agotado", "TEATRO", "Tandil", EstadoEvento.AGOTADO, 20L));
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(preferenciaConFavoritos()));
        given(recomendacionService.recomendar(eq(7L), anyList(), eq(10))).willReturn(List.of());

        endpointService.recomendar(7L);

        ArgumentCaptor<List<EventoCandidato>> captor = ArgumentCaptor.forClass(List.class);
        verify(recomendacionService).recomendar(eq(7L), captor.capture(), eq(10));
        assertThat(captor.getValue())
                .extracting(EventoCandidato::eventoId)
                .containsExactly(5L);
    }

    @Test
    void recomendar_sinPreferenciasNiCiudad_devuelveProximosGenerales() {
        dadoPagina(
                resumen(5L, "Primero", "RECITAL", "Tandil", EstadoEvento.PUBLICADO, 15L),
                resumen(6L, "Segundo", "TEATRO", "Olavarria", EstadoEvento.PUBLICADO, 20L));
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());

        List<RecomendacionResponseDTO> resultado = endpointService.recomendar(7L);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allSatisfy(recomendacion -> {
            assertThat(recomendacion.getPuntaje()).isZero();
            assertThat(recomendacion.getMotivo())
                    .isEqualTo(org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion.PROXIMO);
        });
        verifyNoInteractions(recomendacionService);
    }

    @Test
    void recomendar_sinPreferenciasConCiudad_devuelveProximosDeSuCiudad() {
        dadoPagina(
                resumen(5L, "Tandil", "RECITAL", "Tandil", EstadoEvento.PUBLICADO, 15L),
                resumen(6L, "Olavarria", "TEATRO", "Olavarria", EstadoEvento.PUBLICADO, 20L));
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(
                Preferencia.builder().clienteId(7L).ciudad("Tandil").build()));

        List<RecomendacionResponseDTO> resultado = endpointService.recomendar(7L);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.getEventoId()).isEqualTo(5L);
            assertThat(recomendacion.getPuntaje()).isZero();
            assertThat(recomendacion.getMotivo())
                    .isEqualTo(org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion.CIUDAD);
        });
        verifyNoInteractions(recomendacionService);
    }

    @Test
    void recomendar_conPreferenciasSinCoincidencias_devuelveVacioSinFallback() {
        dadoPagina(resumen(5L, "Recital", "RECITAL", "Tandil", EstadoEvento.PUBLICADO, 15L));
        given(recomendacionService.recomendar(eq(7L), anyList(), eq(10))).willReturn(List.of());
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(
                Preferencia.builder().clienteId(7L)
                        .tiposFavoritos(List.of(
                                org.tudai.ticketya.contract.preferencias.model.TipoEvento.TEATRO))
                        .build()));

        assertThat(endpointService.recomendar(7L)).isEmpty();
    }

    @Test
    void recomendar_siMsEventosFalla_devuelveListaVacia() {
        given(eventosClient.listarEventos(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new RemoteServiceException(502, "ms-eventos caído"));

        assertThat(endpointService.recomendar(7L)).isEmpty();
        verifyNoInteractions(recomendacionService);
    }

    @Test
    void recomendar_consultaEventosFuturosOrdenadosYConElTamanoMaximo() {
        dadoPagina(resumen(5L, "Recital", "RECITAL", "Tandil", EstadoEvento.PUBLICADO, 15L));
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(preferenciaConFavoritos()));
        given(recomendacionService.recomendar(eq(7L), anyList(), eq(10)))
                .willReturn(List.of(new Recomendacion(5L, 5, MotivoRecomendacion.TIPO)));

        endpointService.recomendar(7L);

        verify(eventosClient).listarEventos(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq("fecha"), any(), eq(0), eq(50));
    }

    @Test
    void recomendar_recorreTodasLasPaginasDeCandidatos() {
        PagedEventos primera = new PagedEventos()
                .content(List.of(resumen(5L, "Primero", "RECITAL", "Tandil",
                        EstadoEvento.PUBLICADO, 15L)))
                .page(new PagedLugaresPage().totalPages(2));
        PagedEventos segunda = new PagedEventos()
                .content(List.of(resumen(6L, "Segundo", "TEATRO", "Tandil",
                        EstadoEvento.PUBLICADO, 20L)))
                .page(new PagedLugaresPage().totalPages(2));
        dadoPagina(0, primera);
        dadoPagina(1, segunda);
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(preferenciaConFavoritos()));
        given(recomendacionService.recomendar(eq(7L), anyList(), eq(10))).willReturn(List.of());

        endpointService.recomendar(7L);

        ArgumentCaptor<List<EventoCandidato>> captor = ArgumentCaptor.forClass(List.class);
        verify(recomendacionService).recomendar(eq(7L), captor.capture(), eq(10));
        assertThat(captor.getValue())
                .extracting(EventoCandidato::eventoId)
                .containsExactly(5L, 6L);
    }

    private static Preferencia preferenciaConFavoritos() {
        return Preferencia.builder()
                .clienteId(7L)
                .tiposFavoritos(List.of(
                        org.tudai.ticketya.contract.preferencias.model.TipoEvento.RECITAL))
                .build();
    }

    private void dadoPagina(int numeroPagina, PagedEventos pagina) {
        given(eventosClient.listarEventos(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), eq("fecha"), any(), eq(numeroPagina), eq(50)))
                .willReturn(pagina);
    }

    private void dadoPagina(EventoResumen... resumenes) {
        given(eventosClient.listarEventos(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new PagedEventos().content(List.of(resumenes)));
    }

    private static EventoResumen resumen(Long id, String nombre, String tipo, String ciudad,
                                         EstadoEvento estado, Long participanteId) {
        return new EventoResumen()
                .id(id)
                .nombre(nombre)
                .tipo(org.tudai.ticketya.contract.eventos.model.TipoEvento.valueOf(tipo))
                .fecha(LocalDateTime.of(2026, 12, 1, 20, 0))
                .estado(estado)
                .ciudad(ciudad)
                .participanteIds(List.of(participanteId));
    }
}
