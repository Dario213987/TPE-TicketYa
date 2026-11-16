package org.tudai.ticketya.preferencias.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.domain.EventoCandidato;
import org.tudai.ticketya.preferencias.domain.HistorialCompra;
import org.tudai.ticketya.preferencias.domain.MotivoRecomendacion;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.domain.Recomendacion;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecomendacionServiceTest {

    private static final Instant MANANA = Instant.parse("2026-12-01T20:00:00Z");
    private static final Instant PASADO_MANANA = Instant.parse("2027-01-01T20:00:00Z");

    @Mock
    private PreferenciaRepository preferenciaRepository;

    @Mock
    private CoCompraService coCompraService;

    @InjectMocks
    private RecomendacionService recomendacionService;

    @BeforeEach
    void setUp() {
        given(coCompraService.coOcurrencias(any())).willReturn(Map.of());
    }

    @Test
    void recomendar_conArtistaFavorito_sumaArtista() {
        dadoCliente(preferencia().artistasFavoritos(List.of(15L)).build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(15L), "Tandil", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje()).isEqualTo(RecomendacionService.PESO_ARTISTA);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.ARTISTA);
        });
    }

    @Test
    void recomendar_conArtistaDeUnEventoAsistido_sumaArtista() {
        dadoCliente(preferencia()
                .historial(List.of(historial(4L, TipoEvento.FESTIVAL, List.of(20L), true)))
                .build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(20L), "Tandil", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje()).isEqualTo(RecomendacionService.PESO_ARTISTA);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.ARTISTA);
        });
    }

    @Test
    void recomendar_conTipoFavorito_sumaTipo() {
        dadoCliente(preferencia().tiposFavoritos(List.of(TipoEvento.RECITAL)).build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(), "Tandil", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje()).isEqualTo(RecomendacionService.PESO_TIPO);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.TIPO);
        });
    }

    @Test
    void recomendar_conTipoEnElHistorial_sumaTipo() {
        dadoCliente(preferencia()
                .historial(List.of(historial(4L, TipoEvento.TEATRO, List.of(), false)))
                .build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.TEATRO, List.of(), "Tandil", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje()).isEqualTo(RecomendacionService.PESO_TIPO);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.TIPO);
        });
    }

    @Test
    void recomendar_conCiudadCoincidente_sumaCiudad() {
        dadoCliente(preferencia().ciudad("Tandil").build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.OTRO, List.of(), "  tandil  ", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje()).isEqualTo(RecomendacionService.PESO_CIUDAD);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.CIUDAD);
        });
    }

    @Test
    void recomendar_conCoCompra_sumaHastaElTope() {
        dadoCliente(preferencia()
                .historial(List.of(historial(4L, TipoEvento.FESTIVAL, List.of(), false)))
                .build());
        given(coCompraService.coOcurrencias(any())).willReturn(Map.of(5L, 10L));

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.OTRO, List.of(), "Olavarria", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje()).isEqualTo(RecomendacionService.PESO_MAX_CO_COMPRA);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.CO_COMPRA);
        });
    }

    @Test
    void recomendar_sinHistorial_usaSoloLasPreferencias() {
        dadoCliente(preferencia()
                .tiposFavoritos(List.of(TipoEvento.RECITAL))
                .artistasFavoritos(List.of(15L))
                .ciudad("Tandil")
                .build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(15L), "Tandil", MANANA)), 10);

        assertThat(resultado).singleElement().satisfies(recomendacion -> {
            assertThat(recomendacion.puntaje())
                    .isEqualTo(RecomendacionService.PESO_ARTISTA
                            + RecomendacionService.PESO_TIPO
                            + RecomendacionService.PESO_CIUDAD);
            assertThat(recomendacion.motivo()).isEqualTo(MotivoRecomendacion.ARTISTA);
        });
    }

    @Test
    void recomendar_sinPreferenciasNiHistorial_devuelveListaVacia() {
        dadoCliente(preferencia().build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.OTRO, List.of(), "Olavarria", MANANA)), 10);

        assertThat(resultado).isEmpty();
    }

    @Test
    void recomendar_excluyeEventosParaLosQueYaTieneEntrada() {
        dadoCliente(preferencia()
                .tiposFavoritos(List.of(TipoEvento.RECITAL))
                .historial(List.of(historial(5L, TipoEvento.RECITAL, List.of(), true)))
                .build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(), "Tandil", MANANA)), 10);

        assertThat(resultado).isEmpty();
    }

    @Test
    void recomendar_ordenaPorPuntajeYDesempataPorFechaYId() {
        dadoCliente(preferencia()
                .tiposFavoritos(List.of(TipoEvento.RECITAL))
                .ciudad("Tandil")
                .build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(), "Tandil", PASADO_MANANA),
                candidato(6L, TipoEvento.RECITAL, List.of(), "Olavarria", MANANA),
                candidato(7L, TipoEvento.RECITAL, List.of(), "Tandil", MANANA)), 10);

        assertThat(resultado)
                .extracting(Recomendacion::eventoId)
                .containsExactly(7L, 5L, 6L);
    }

    @Test
    void recomendar_respetaElLimite() {
        dadoCliente(preferencia().tiposFavoritos(List.of(TipoEvento.RECITAL)).build());

        List<Recomendacion> resultado = recomendacionService.recomendar(7L, List.of(
                candidato(5L, TipoEvento.RECITAL, List.of(), "Tandil", MANANA),
                candidato(6L, TipoEvento.RECITAL, List.of(), "Tandil", MANANA)), 1);

        assertThat(resultado).singleElement()
                .satisfies(recomendacion -> assertThat(recomendacion.eventoId()).isEqualTo(5L));
    }

    private void dadoCliente(Preferencia preferencia) {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(preferencia));
    }

    private static Preferencia.PreferenciaBuilder preferencia() {
        return Preferencia.builder().clienteId(7L);
    }

    private static EventoCandidato candidato(Long eventoId, TipoEvento tipo, List<Long> participantes,
                                             String ciudad, Instant fecha) {
        return new EventoCandidato(eventoId, tipo, participantes, ciudad, fecha);
    }

    private static HistorialCompra historial(Long eventoId, TipoEvento tipo, List<Long> participantes,
                                             boolean usado) {
        return HistorialCompra.builder()
                .eventoId(eventoId)
                .tipo(tipo)
                .participanteIds(participantes)
                .ciudad("Tandil")
                .fecha(MANANA)
                .comprado(true)
                .usado(usado)
                .build();
    }
}
