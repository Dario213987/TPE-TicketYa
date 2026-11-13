package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.config.ReservaProperties;
import org.tudai.ticketya.tickets.domain.DisponibilidadAsiento;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.dto.AsientoDisponibilidadDTO;
import org.tudai.ticketya.tickets.dto.DisponibilidadEventoResponseDTO;
import org.tudai.ticketya.tickets.dto.DisponibilidadResponseDTO;
import org.tudai.ticketya.tickets.dto.PublicarEventoRequestDTO;
import org.tudai.ticketya.tickets.dto.SectorDisponibilidadDTO;
import org.tudai.ticketya.tickets.dto.SectorDisponibilidadResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoBatchRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private DisponibilidadSectorRepository sectorRepository;

    @Mock
    private DisponibilidadAsientoRepository asientoRepository;

    @Mock
    private DisponibilidadAsientoBatchRepository asientoBatchRepository;

    @Mock
    private ReservaVencidaService reservaVencidaService;

    private DisponibilidadService service;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadService(
                eventoRepository, sectorRepository, asientoRepository, asientoBatchRepository,
                new ReservaProperties(), reservaVencidaService);
    }

    @Test
    void consultar_cuandoNoHaySectores_lanzaNotFoundException() {
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(99L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.consultar(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void consultar_sectorNoNumerado_usaElDisponibleDelSectorYNoCargaAsientos() {
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 500, 320);
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(7L)).thenReturn(List.of(sector));

        DisponibilidadEventoResponseDTO respuesta = service.consultar(7L);

        assertThat(respuesta.getEventoId()).isEqualTo(7L);
        assertThat(respuesta.getSectores()).hasSize(1);

        SectorDisponibilidadResponseDTO sectorDto = respuesta.getSectores().get(0);
        assertThat(sectorDto.getSectorId()).isEqualTo(3L);
        assertThat(sectorDto.getTipoSector()).isEqualTo(TipoSector.NO_NUMERADO);
        assertThat(sectorDto.getCapacidadTotal()).isEqualTo(500);
        assertThat(sectorDto.getDisponibles()).isEqualTo(320);
        assertThat(sectorDto.getAsientos()).isNull();

        verify(reservaVencidaService).liberarVencidas(any(), any(Instant.class));
        verifyNoInteractions(asientoRepository);
    }

    @Test
    void consultar_cuandoNoHaySectores_noLiberaReservas() {
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(99L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.consultar(99L))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(reservaVencidaService);
    }

    @Test
    void publicar_cuandoElEventoYaExiste_procesaLosSectoresNuevos() {
        DisponibilidadEvento existente = DisponibilidadEvento.builder()
                .eventoId(7L)
                .creadoEl(Instant.parse("2026-01-01T00:00:00Z"))
                .limiteEntradasPorCliente(4)
                .build();
        when(eventoRepository.insertarSiNoExiste(any(), any(Instant.class), any()))
                .thenReturn(0);
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.empty());

        DisponibilidadResponseDTO respuesta = service.publicar(PublicarEventoRequestDTO.builder()
                .eventoId(7L)
                .limiteEntradasPorCliente(4)
                .sectores(List.of(SectorDisponibilidadDTO.builder()
                        .sectorId(3L)
                        .tipo(TipoSector.NO_NUMERADO)
                        .capacidadMaxima(100)
                        .build()))
                .build());

        assertThat(respuesta.isYaExistia()).isTrue();
        assertThat(respuesta.getSectoresProcesados()).isEqualTo(1);
        assertThat(respuesta.getCreadoEl()).isEqualTo(existente.getCreadoEl());
        verify(sectorRepository).save(any(DisponibilidadSector.class));
    }

    @Test
    void consultar_sectorNumerado_cuentaSoloLosLibresYListaLosAsientos() {
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 3, 99);
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(7L)).thenReturn(List.of(sector));
        when(asientoRepository.findBySector_IdOrderByFilaAscNumeroAsc(10L)).thenReturn(List.of(
                asiento(1, 1, EstadoDisponibilidad.LIBRE),
                asiento(1, 2, EstadoDisponibilidad.RESERVADO),
                asiento(2, 1, EstadoDisponibilidad.LIBRE)));

        SectorDisponibilidadResponseDTO sectorDto = service.consultar(7L).getSectores().get(0);

        assertThat(sectorDto.getDisponibles()).isEqualTo(2);
        assertThat(sectorDto.getAsientos())
                .extracting(AsientoDisponibilidadDTO::getFila,
                        AsientoDisponibilidadDTO::getNumero,
                        AsientoDisponibilidadDTO::getEstado)
                .containsExactly(
                        tuple(1, 1, EstadoDisponibilidad.LIBRE),
                        tuple(1, 2, EstadoDisponibilidad.RESERVADO),
                        tuple(2, 1, EstadoDisponibilidad.LIBRE));

        verify(asientoRepository).findBySector_IdOrderByFilaAscNumeroAsc(10L);
    }

    @Test
    void consultar_sectorNumeradoSinAsientosLibres_devuelveCeroDisponibles() {
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 2, 99);
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(7L)).thenReturn(List.of(sector));
        when(asientoRepository.findBySector_IdOrderByFilaAscNumeroAsc(10L)).thenReturn(List.of(
                asiento(1, 1, EstadoDisponibilidad.VENDIDO),
                asiento(1, 2, EstadoDisponibilidad.RESERVADO)));

        SectorDisponibilidadResponseDTO sectorDto = service.consultar(7L).getSectores().get(0);

        assertThat(sectorDto.getDisponibles()).isZero();
        assertThat(sectorDto.getAsientos()).hasSize(2);
    }

    @Test
    void consultar_conSectoresNumeradosYNoNumerados_mapeaCadaUnoSegunSuTipo() {
        DisponibilidadSector numerado = sector(10L, 3L, TipoSector.NUMERADO, 2, 99);
        DisponibilidadSector noNumerado = sector(11L, 4L, TipoSector.NO_NUMERADO, 50, 20);
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(7L))
                .thenReturn(List.of(numerado, noNumerado));
        when(asientoRepository.findBySector_IdOrderByFilaAscNumeroAsc(10L)).thenReturn(List.of(
                asiento(1, 1, EstadoDisponibilidad.LIBRE),
                asiento(1, 2, EstadoDisponibilidad.LIBRE)));

        DisponibilidadEventoResponseDTO respuesta = service.consultar(7L);

        assertThat(respuesta.getSectores())
                .extracting(SectorDisponibilidadResponseDTO::getSectorId,
                        SectorDisponibilidadResponseDTO::getDisponibles)
                .containsExactly(tuple(3L, 2), tuple(4L, 20));

        verify(asientoRepository).findBySector_IdOrderByFilaAscNumeroAsc(10L);
        verify(asientoRepository, never()).findBySector_IdOrderByFilaAscNumeroAsc(11L);
    }

    @Test
    void consultar_sinLimiteConfigurado_devuelveElValorPorDefecto() {
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 500, 320);
        when(sectorRepository.findByEvento_EventoIdOrderByIdAsc(7L)).thenReturn(List.of(sector));
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(
                DisponibilidadEvento.builder()
                        .eventoId(7L)
                        .creadoEl(Instant.now())
                        .build()));

        DisponibilidadEventoResponseDTO respuesta = service.consultar(7L);

        assertThat(respuesta.getLimiteEntradasPorCliente()).isEqualTo(4);
    }

    @Test
    void publicar_conOrganizadorYPrecio_losGuardaParaElPago() {
        DisponibilidadEvento existente = DisponibilidadEvento.builder()
                .eventoId(7L)
                .creadoEl(Instant.parse("2026-01-01T00:00:00Z"))
                .limiteEntradasPorCliente(4)
                .build();
        when(eventoRepository.insertarSiNoExiste(any(), any(Instant.class), any()))
                .thenReturn(0);
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.empty());

        service.publicar(PublicarEventoRequestDTO.builder()
                .eventoId(7L)
                .organizadorId(5L)
                .sectores(List.of(SectorDisponibilidadDTO.builder()
                        .sectorId(3L)
                        .tipo(TipoSector.NO_NUMERADO)
                        .capacidadMaxima(100)
                        .precio(1500.0)
                        .build()))
                .build());

        org.mockito.ArgumentCaptor<DisponibilidadSector> guardado =
                org.mockito.ArgumentCaptor.forClass(DisponibilidadSector.class);
        verify(sectorRepository).save(guardado.capture());
        assertThat(guardado.getValue().getPrecio()).isEqualTo(1500.0);
        assertThat(existente.getOrganizadorId()).isEqualTo(5L);
        verify(eventoRepository).save(existente);
    }

    private DisponibilidadSector sector(Long id, Long sectorIdExterno, TipoSector tipo,
                                        int capacidadTotal, int disponibles) {
        return DisponibilidadSector.builder()
                .id(id)
                .sectorIdExterno(sectorIdExterno)
                .tipo(tipo)
                .capacidadTotal(capacidadTotal)
                .disponibles(disponibles)
                .build();
    }

    private DisponibilidadAsiento asiento(int fila, int numero, EstadoDisponibilidad estado) {
        return DisponibilidadAsiento.builder()
                .fila(fila)
                .numero(numero)
                .estado(estado)
                .build();
    }
}
