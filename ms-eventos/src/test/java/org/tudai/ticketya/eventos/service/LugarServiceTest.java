package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.config.ModelMapperConfig;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.contract.eventos.model.LugarCreateRequest;
import org.tudai.ticketya.contract.eventos.model.LugarDetalleResponse;
import org.tudai.ticketya.contract.eventos.model.LugarResponse;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LugarServiceTest {

    @Mock
    private LugarRepository lugarRepository;

    private ModelMapper modelMapper;
    private LugarService lugarService;

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapperConfig().modelMapper();
        lugarService = new LugarService(lugarRepository, modelMapper);
    }

    @Test
    void crearLugar_mapeaUbicacionYDevuelveElIdGenerado() {
        LugarCreateRequest dto = new LugarCreateRequest()
                .nombre("Estadio Municipal")
                .tipo("ESTADIO")
                .direccion("Av. Siempre Viva 742")
                .ciudad("Rosario")
                .latitud(-32.95)
                .longitud(-60.64)
                .capacidad(30000);
        when(lugarRepository.save(any(Lugar.class))).thenAnswer(invocation -> {
            Lugar lugar = invocation.getArgument(0);
            lugar.setId(7L);
            return lugar;
        });

        LugarResponse response = lugarService.crearLugar(dto);

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getNombre()).isEqualTo("Estadio Municipal");
        assertThat(response.getCiudad()).isEqualTo("Rosario");
        assertThat(response.getLatitud()).isEqualTo(-32.95);
        assertThat(response.getLongitud()).isEqualTo(-60.64);

        ArgumentCaptor<Lugar> captor = ArgumentCaptor.forClass(Lugar.class);
        verify(lugarRepository).save(captor.capture());
        assertThat(captor.getValue().getUbicacion().getLatitud()).isEqualTo(-32.95);
        assertThat(captor.getValue().getUbicacion().getLongitud()).isEqualTo(-60.64);
    }

    @Test
    void listarLugares_sinCiudad_usaFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Lugar lugar = lugar(1L, "Estadio Municipal", "Rosario");
        when(lugarRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(lugar)));

        Page<LugarDetalleResponse> result = lugarService.listarLugares(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getNombre()).isEqualTo("Estadio Municipal");
        assertThat(result.getContent().getFirst().getLatitud()).isEqualTo(-32.95);
        verify(lugarRepository).findAll(pageable);
        verify(lugarRepository, never()).findByCiudadIgnoreCase(any(), any());
    }

    @Test
    void listarLugares_conCiudad_limpiaElValorYFiltraPorCiudad() {
        Pageable pageable = PageRequest.of(0, 10);
        Lugar lugar = lugar(2L, "Teatro El Círculo", "Rosario");
        when(lugarRepository.findByCiudadIgnoreCase("Rosario", pageable))
                .thenReturn(new PageImpl<>(List.of(lugar)));

        Page<LugarDetalleResponse> result = lugarService.listarLugares("  Rosario  ", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getCiudad()).isEqualTo("Rosario");
        verify(lugarRepository).findByCiudadIgnoreCase("Rosario", pageable);
        verify(lugarRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listarLugares_conCiudadEnBlanco_usaFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(lugarRepository.findAll(pageable)).thenReturn(Page.empty());

        lugarService.listarLugares("   ", pageable);

        verify(lugarRepository).findAll(pageable);
        verify(lugarRepository, never()).findByCiudadIgnoreCase(any(), any());
    }

    @Test
    void obtenerLugar_existente_devuelveElDetalleConSectores() {
        Lugar lugar = lugar(3L, "Estadio Municipal", "Rosario");
        when(lugarRepository.findById(3L)).thenReturn(java.util.Optional.of(lugar));

        LugarDetalleResponse response = lugarService.obtenerLugar(3L);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getSectores()).isEmpty();
    }

    @Test
    void obtenerLugar_inexistente_lanzaNotFound() {
        when(lugarRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> lugarService.obtenerLugar(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Lugar no encontrado con id: 99");
    }

    private static Lugar lugar(Long id, String nombre, String ciudad) {
        return Lugar.builder()
                .id(id)
                .nombre(nombre)
                .tipo("ESTADIO")
                .direccion("Av. Siempre Viva 742")
                .ciudad(ciudad)
                .capacidad(30000)
                .ubicacion(Ubicacion.builder().latitud(-32.95).longitud(-60.64).build())
                .build();
    }
}
