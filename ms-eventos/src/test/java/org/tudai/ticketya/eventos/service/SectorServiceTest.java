package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.config.ModelMapperConfig;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.contract.eventos.model.SectorNoNumeradoRequest;
import org.tudai.ticketya.contract.eventos.model.SectorNumeradoRequest;
import org.tudai.ticketya.contract.eventos.model.SectorResponse;
import org.tudai.ticketya.contract.eventos.model.TipoSector;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectorServiceTest {

    @Mock
    private LugarRepository lugarRepository;

    private ModelMapper modelMapper;
    private SectorService sectorService;

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapperConfig().modelMapper();
        sectorService = new SectorService(lugarRepository, modelMapper);
    }

    @Test
    void crearSectorNoNumerado_agregaElSectorAlLugarYLoDevuelve() {
        Lugar lugar = Lugar.builder().id(1L).nombre("Estadio").build();
        when(lugarRepository.findById(1L)).thenReturn(Optional.of(lugar));
        when(lugarRepository.save(any(Lugar.class))).thenReturn(lugar);

        SectorNoNumeradoRequest dto = new SectorNoNumeradoRequest()
                .nombre("Campo")
                .precio(15000.0)
                .capacidadMaxima(500);

        SectorResponse response = sectorService.crearSectorNoNumerado(1L, dto);

        assertThat(response.getNombre()).isEqualTo("Campo");
        assertThat(response.getPrecio()).isEqualTo(15000.0);
        assertThat(response.getTipoSector()).isEqualTo(TipoSector.NO_NUMERADO);
        assertThat(response.getCapacidadMaxima()).isEqualTo(500);
        assertThat(response.getCapacidad()).isEqualTo(500);
        assertThat(lugar.getSectores()).hasSize(1);
        assertThat(lugar.getSectores().iterator().next()).isInstanceOf(SectorNoNumerado.class);
        verify(lugarRepository).save(lugar);
    }

    @Test
    void crearSectorNumerado_generaLosAsientosYDevuelveLosTotales() {
        Lugar lugar = Lugar.builder().id(1L).nombre("Estadio").build();
        when(lugarRepository.findById(1L)).thenReturn(Optional.of(lugar));
        when(lugarRepository.save(any(Lugar.class))).thenReturn(lugar);

        SectorNumeradoRequest dto = new SectorNumeradoRequest()
                .nombre("Platea Alta")
                .precio(20000.0)
                .filas(2)
                .asientosPorFila(3);

        SectorResponse response = sectorService.crearSectorNumerado(1L, dto);

        assertThat(response.getNombre()).isEqualTo("Platea Alta");
        assertThat(response.getPrecio()).isEqualTo(20000.0);
        assertThat(response.getTipoSector()).isEqualTo(TipoSector.NUMERADO);
        assertThat(response.getCapacidad()).isEqualTo(6);
        assertThat(response.getFilas()).isEqualTo(2);
        assertThat(response.getAsientosPorFila()).isEqualTo(3);

        SectorNumerado sector = (SectorNumerado) lugar.getSectores().iterator().next();
        assertThat(sector.getAsientos()).hasSize(6);
        verify(lugarRepository).save(lugar);
    }

    @Test
    void crearSectorNoNumerado_conLugarInexistente_lanzaNotFound() {
        when(lugarRepository.findById(99L)).thenReturn(Optional.empty());

        SectorNoNumeradoRequest dto = new SectorNoNumeradoRequest()
                .nombre("Campo")
                .precio(15000.0)
                .capacidadMaxima(500);

        assertThatThrownBy(() -> sectorService.crearSectorNoNumerado(99L, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Lugar no encontrado con id: 99");
    }

    @Test
    void crearSectorNumerado_conLugarInexistente_lanzaNotFound() {
        when(lugarRepository.findById(99L)).thenReturn(Optional.empty());

        SectorNumeradoRequest dto = new SectorNumeradoRequest()
                .nombre("Platea Alta")
                .precio(20000.0)
                .filas(2)
                .asientosPorFila(3);

        assertThatThrownBy(() -> sectorService.crearSectorNumerado(99L, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Lugar no encontrado con id: 99");
    }
}
