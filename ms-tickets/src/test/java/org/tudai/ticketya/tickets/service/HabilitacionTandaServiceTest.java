package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.config.HabilitacionProperties;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.HabilitacionEvento;
import org.tudai.ticketya.tickets.dto.HabilitacionRequestDTO;
import org.tudai.ticketya.tickets.repository.HabilitacionEventoRepository;
import org.tudai.ticketya.tickets.repository.PaseRedisRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabilitacionTandaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private HabilitacionEventoRepository habilitacionRepository;

    @Mock
    private PaseRedisRepository paseRepository;

    private HabilitacionProperties properties;
    private HabilitacionTandaService service;

    @BeforeEach
    void setUp() {
        properties = new HabilitacionProperties();
        properties.setTamanoTanda(10);
        properties.setIntervalo(Duration.ofSeconds(30));
        properties.setTtlPase(Duration.ofMinutes(5));
        service = new HabilitacionTandaService(
                habilitacionRepository, paseRepository, properties);
    }

    @Test
    void ejecutarTandas_siEstaDeshabilitado_noTocaNada() {
        properties.setEnabled(false);

        service.ejecutarTandas();

        verifyNoInteractions(habilitacionRepository, paseRepository);
    }

    @Test
    void ejecutarTandas_reclamaLaTandaYEmitirLosPases() {
        HabilitacionEvento config = config(7L, true);
        arrangeTanda(config, 1);
        when(paseRepository.habilitarSiguientes(
                eq(7L), eq(4), eq(Duration.ofSeconds(90)), anyString()))
                .thenReturn(List.of(pase(1L), pase(2L)));

        service.ejecutarTandas();

        verify(habilitacionRepository).insertarFaltantes(
                10, 30, 300, AHORA);
        verify(habilitacionRepository).reclamar(7L, AHORA, AHORA.plusSeconds(60));
        verify(habilitacionRepository).incrementarHabilitados(7L, 2);
    }

    @Test
    void ejecutarTandas_siOtraInstanciaReclamoLaTanda_noEmitePases() {
        HabilitacionEvento config = config(7L, true);
        arrangeTanda(config, 0);

        service.ejecutarTandas();

        verify(paseRepository, never()).habilitarSiguientes(
                anyLong(), anyInt(), any(), anyString());
        verify(habilitacionRepository, never()).incrementarHabilitados(anyLong(), anyInt());
    }

    @Test
    void ejecutarTandas_siRedisFalla_reprogramaLaTanda() {
        HabilitacionEvento config = config(7L, true);
        arrangeTanda(config, 1);
        when(paseRepository.habilitarSiguientes(
                eq(7L), eq(4), eq(Duration.ofSeconds(90)), anyString()))
                .thenThrow(new IllegalStateException("Redis caido"));

        service.ejecutarTandas();

        verify(habilitacionRepository).reprogramar(7L, AHORA);
        verify(habilitacionRepository, never()).incrementarHabilitados(anyLong(), anyInt());
    }

    @Test
    void ejecutarTandas_siLaConfiguracionEstaDeshabilitada_noEmitePases() {
        HabilitacionEvento config = config(7L, false);
        arrangeTanda(config, 1);

        service.ejecutarTandas();

        verify(paseRepository, never()).habilitarSiguientes(
                anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void requierePase_soloParaEventosDeAltaDemandaConConfiguracionActiva() {
        DisponibilidadEvento altaDemanda = DisponibilidadEvento.builder()
                .eventoId(7L).altaDemanda(true).build();
        DisponibilidadEvento sinAltaDemanda = DisponibilidadEvento.builder()
                .eventoId(7L).altaDemanda(false).build();
        when(habilitacionRepository.findById(7L))
                .thenReturn(Optional.of(config(7L, true)));

        assertThat(service.requierePase(7L, altaDemanda)).isTrue();
        assertThat(service.requierePase(7L, sinAltaDemanda)).isFalse();
        assertThat(service.requierePase(9L, altaDemanda)).isFalse();
    }

    @Test
    void requierePase_falseSiLaHabilitacionGlobalEstaDeshabilitada() {
        properties.setEnabled(false);

        assertThat(service.requierePase(7L, DisponibilidadEvento.builder()
                .eventoId(7L).altaDemanda(true).build())).isFalse();

        verifyNoInteractions(habilitacionRepository);
    }

    @Test
    void validarPase_sinPaseVigente_lanzaConflict() {
        when(paseRepository.validar(7L, 42L, "token")).thenReturn(false);

        assertThatThrownBy(() -> service.validarPase(7L, 42L, "token"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cliente 42")
                .hasMessageContaining("evento 7");
    }

    @Test
    void validarPase_conPaseVigente_noFalla() {
        when(paseRepository.validar(7L, 42L, "token")).thenReturn(true);

        service.validarPase(7L, 42L, "token");

        verify(paseRepository).validar(7L, 42L, "token");
    }

    @Test
    void configurar_actualizaSoloLosCamposInformados() {
        HabilitacionEvento config = config(7L, true);
        when(habilitacionRepository.findById(7L)).thenReturn(Optional.of(config));
        HabilitacionRequestDTO request = HabilitacionRequestDTO.builder()
                .tamanoTanda(20)
                .ttlPaseSegundos(120)
                .build();

        service.configurar(7L, request);

        ArgumentCaptor<HabilitacionEvento> captor =
                ArgumentCaptor.forClass(HabilitacionEvento.class);
        verify(habilitacionRepository).save(captor.capture());
        assertThat(captor.getValue().getTamanoTanda()).isEqualTo(20);
        assertThat(captor.getValue().getTtlPaseSegundos()).isEqualTo(120);
        assertThat(captor.getValue().getIntervaloSegundos()).isEqualTo(60);
        assertThat(captor.getValue().isHabilitada()).isTrue();
    }

    @Test
    void configurar_sinConfiguracion_lanzaNotFound() {
        when(habilitacionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.configurar(7L, new HabilitacionRequestDTO()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("evento 7");
    }

    @Test
    void configurar_conValoresInvalidos_lanzaBadRequest() {
        when(habilitacionRepository.findById(7L))
                .thenReturn(Optional.of(config(7L, true)));

        assertThatThrownBy(() -> service.configurar(7L,
                HabilitacionRequestDTO.builder().tamanoTanda(0).build()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.configurar(7L,
                HabilitacionRequestDTO.builder().intervaloSegundos(0).build()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.configurar(7L,
                HabilitacionRequestDTO.builder().ttlPaseSegundos(0).build()))
                .isInstanceOf(BadRequestException.class);
        verify(habilitacionRepository, never()).save(any());
    }

    @Test
    void sincronizar_conAltaDemanda_creaLaConfiguracionYLaReactiva() {
        service.sincronizar(7L, true);

        verify(habilitacionRepository).insertarSiNoExiste(
                eq(7L), eq(10), eq(30), eq(300), any());
        verify(habilitacionRepository).reactivar(eq(7L), any());
        verify(habilitacionRepository, never()).deshabilitar(any());
    }

    @Test
    void sincronizar_sinAltaDemanda_deshabilitaLaConfiguracion() {
        service.sincronizar(7L, false);

        verify(habilitacionRepository).deshabilitar(7L);
        verify(habilitacionRepository, never()).insertarSiNoExiste(
                any(), anyInt(), anyInt(), anyInt(), any());
    }

    private void arrangeTanda(HabilitacionEvento config, int reclamo) {
        lenient().when(habilitacionRepository.ahora())
                .thenReturn(Timestamp.from(AHORA));
        lenient().when(habilitacionRepository.buscarVencidos(AHORA))
                .thenReturn(List.of(7L));
        lenient().when(habilitacionRepository.findById(7L))
                .thenReturn(Optional.of(config));
        lenient().when(habilitacionRepository.reclamar(7L, AHORA, AHORA.plusSeconds(60)))
                .thenReturn(reclamo);
    }

    private HabilitacionEvento config(Long eventoId, boolean habilitada) {
        return HabilitacionEvento.builder()
                .eventoId(eventoId)
                .habilitada(habilitada)
                .tamanoTanda(4)
                .intervaloSegundos(60)
                .ttlPaseSegundos(90)
                .proximaTandaEl(AHORA.minusSeconds(5))
                .totalHabilitados(0)
                .build();
    }

    private PaseRedisRepository.Pase pase(Long clienteId) {
        return new PaseRedisRepository.Pase(
                clienteId, "tanda: " + clienteId, "tanda", 1L,
                AHORA, AHORA.plusSeconds(90));
    }
}
