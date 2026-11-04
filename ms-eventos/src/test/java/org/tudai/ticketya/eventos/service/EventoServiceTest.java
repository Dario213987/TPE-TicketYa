package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.config.ModelMapperConfig;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoUpdateDTO;
import org.tudai.ticketya.eventos.dto.evento.PrecioEventoSectorResponseDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private LugarRepository lugarRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private EventoService eventoService;

    @Test
    void losEventosAgotadosSiguenSiendoVisibles() {
        assertThat(EstadoEvento.VISIBLES)
                .containsExactlyInAnyOrder(
                        EstadoEvento.CONFIRMADO,
                        EstadoEvento.FINALIZADO,
                        EstadoEvento.AGOTADO);
    }

    @Test
    void listarPublicados_consultaLosEstadosVisibles() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Evento> page = new PageImpl<>(List.of(Evento.builder().id(1L).build()));
        when(eventoRepository.findByEstadoIn(EstadoEvento.VISIBLES, pageable)).thenReturn(page);

        assertThat(eventoService.listarPublicados(pageable)).isSameAs(page);
        verify(eventoRepository).findByEstadoIn(EstadoEvento.VISIBLES, pageable);
    }

    @Test
    void definirPrecioSector_enEventoBorradorPropio_guardaElPrecioParaElEvento() {
        SectorNumerado sector = SectorNumerado.builder().id(100L).nombre("Platea").build();
        Lugar lugar = Lugar.builder().id(10L).nombre("Estadio").build();
        lugar.addSector(sector);
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .lugar(lugar)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        PrecioEventoSectorResponseDTO response =
                eventoService.definirPrecioSector(1L, 100L, 15000.0, 7L, false);

        assertThat(evento.getPreciosSectores()).containsEntry(100L, 15000.0);
        assertThat(response.getEventoId()).isEqualTo(1L);
        assertThat(response.getSectorId()).isEqualTo(100L);
        assertThat(response.getPrecio()).isEqualTo(15000.0);
        verify(eventoRepository).save(evento);
    }

    @Test
    void definirPrecioSector_reemplazaElPrecioExistente() {
        SectorNumerado sector = SectorNumerado.builder().id(100L).nombre("Platea").build();
        Lugar lugar = Lugar.builder().id(10L).nombre("Estadio").build();
        lugar.addSector(sector);
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .lugar(lugar)
                .build();
        evento.definirPrecioSector(100L, 10000.0);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        eventoService.definirPrecioSector(1L, 100L, 20000.0, 7L, false);

        assertThat(evento.getPreciosSectores()).containsEntry(100L, 20000.0).hasSize(1);
    }

    @Test
    void definirPrecioSector_conSectorDeOtroLugar_lanzaBadRequest() {
        SectorNumerado sector = SectorNumerado.builder().id(100L).nombre("Platea").build();
        Lugar lugar = Lugar.builder().id(10L).nombre("Estadio").build();
        lugar.addSector(sector);
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .lugar(lugar)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.definirPrecioSector(1L, 999L, 15000.0, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El sector 999 no pertenece al lugar del evento");
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void definirPrecioSector_enEventoPublicado_lanzaConflict() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.CONFIRMADO)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.definirPrecioSector(1L, 100L, 15000.0, 7L, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Solo se pueden definir precios en eventos en estado BORRADOR");
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void definirPrecioSector_enEventoDeOtroOrganizador_lanzaForbidden() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(8L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.definirPrecioSector(1L, 100L, 15000.0, 7L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("No podés definir precios en un evento que no te pertenece");
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void definirPrecioSector_comoAdministrador_permiteEventoDeOtroOrganizador() {
        SectorNumerado sector = SectorNumerado.builder().id(100L).nombre("Platea").build();
        Lugar lugar = Lugar.builder().id(10L).nombre("Estadio").build();
        lugar.addSector(sector);
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(8L)
                .lugar(lugar)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        eventoService.definirPrecioSector(1L, 100L, 15000.0, 7L, true);

        assertThat(evento.getPreciosSectores()).containsEntry(100L, 15000.0);
    }

    @Test
    void definirPrecioSector_conPrecioNoPositivo_lanzaBadRequest() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.definirPrecioSector(1L, 100L, 0.0, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El precio debe ser mayor a 0");
    }

    @Test
    void definirPrecioSector_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.definirPrecioSector(99L, 100L, 15000.0, 7L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Evento no encontrado con id: 99");
    }

    @Test
    void actualizarEvento_enBorradorPropio_aplicaCambiosValidos() {
        Evento evento = Evento.builder()
                .id(1L)
                .nombre("Nombre viejo")
                .descripcion("Descripción vieja")
                .tipo("RECITAL")
                .fecha(LocalDate.of(2026, 12, 1))
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoUpdateDTO dto = EventoUpdateDTO.builder()
                .nombre("Nombre nuevo")
                .descripcion("Descripción nueva")
                .tipo("TEATRO")
                .build();

        EventoResponseDTO response = eventoService.actualizarEvento(1L, dto, 7L, false);

        assertThat(evento.getNombre()).isEqualTo("Nombre nuevo");
        assertThat(evento.getDescripcion()).isEqualTo("Descripción nueva");
        assertThat(evento.getTipo()).isEqualTo("TEATRO");
        assertThat(response.getNombre()).isEqualTo("Nombre nuevo");
        verify(eventoRepository).save(evento);
    }

    @Test
    void actualizarEvento_enBorradorPropio_permiteCambiarFechaYLugar() {
        Lugar lugarViejo = Lugar.builder().id(10L).nombre("Viejo").build();
        Lugar lugarNuevo = Lugar.builder().id(20L).nombre("Nuevo").build();
        Evento evento = Evento.builder()
                .id(1L)
                .fecha(LocalDate.of(2026, 12, 1))
                .lugar(lugarViejo)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(lugarRepository.findById(20L)).thenReturn(Optional.of(lugarNuevo));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoUpdateDTO dto = EventoUpdateDTO.builder()
                .fecha(LocalDate.of(2026, 12, 15))
                .lugarId(20L)
                .build();

        eventoService.actualizarEvento(1L, dto, 7L, false);

        assertThat(evento.getFecha()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(evento.getLugar()).isEqualTo(lugarNuevo);
    }

    @Test
    void actualizarEvento_enEventoPublicado_rechazaCambioDeFecha() {
        LocalDate fechaOriginal = LocalDate.of(2026, 12, 1);
        Evento evento = Evento.builder()
                .id(1L)
                .fecha(fechaOriginal)
                .estado(EstadoEvento.CONFIRMADO)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        EventoUpdateDTO dto = EventoUpdateDTO.builder()
                .fecha(LocalDate.of(2026, 12, 15))
                .build();

        assertThatThrownBy(() -> eventoService.actualizarEvento(1L, dto, 7L, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No se puede modificar la fecha");
        assertThat(evento.getFecha()).isEqualTo(fechaOriginal);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void actualizarEvento_enEventoPublicado_permiteCambiosNoEstructurales() {
        Evento evento = Evento.builder()
                .id(1L)
                .nombre("Nombre viejo")
                .estado(EstadoEvento.CONFIRMADO)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoUpdateDTO dto = EventoUpdateDTO.builder().nombre("Nombre nuevo").build();

        eventoService.actualizarEvento(1L, dto, 7L, false);

        assertThat(evento.getNombre()).isEqualTo("Nombre nuevo");
        verify(eventoRepository).save(evento);
    }

    @Test
    void actualizarEvento_enEventoDeOtroOrganizador_lanzaForbidden() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(8L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        EventoUpdateDTO dto = EventoUpdateDTO.builder().nombre("Nombre nuevo").build();

        assertThatThrownBy(() -> eventoService.actualizarEvento(1L, dto, 7L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("No podés modificar un evento que no te pertenece");
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void actualizarEvento_comoAdministrador_permiteEventoDeOtroOrganizador() {
        Evento evento = Evento.builder()
                .id(1L)
                .nombre("Nombre viejo")
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(8L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        eventoService.actualizarEvento(1L, EventoUpdateDTO.builder().nombre("Nombre nuevo").build(), 7L, true);

        assertThat(evento.getNombre()).isEqualTo("Nombre nuevo");
    }

    @Test
    void actualizarEvento_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.actualizarEvento(
                99L, EventoUpdateDTO.builder().nombre("Nombre").build(), 7L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Evento no encontrado con id: 99");
    }

    @Test
    void eliminarEvento_enBorradorPropio_loElimina() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        eventoService.eliminarEvento(1L, 7L, false);

        verify(eventoRepository).delete(evento);
    }

    @Test
    void eliminarEvento_enEventoPublicado_lanzaConflict() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.CONFIRMADO)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.eliminarEvento(1L, 7L, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Solo se pueden eliminar eventos en estado BORRADOR");
        verify(eventoRepository, never()).delete(any(Evento.class));
    }

    @Test
    void eliminarEvento_enEventoDeOtroOrganizador_lanzaForbidden() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(8L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.eliminarEvento(1L, 7L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("No podés eliminar un evento que no te pertenece");
        verify(eventoRepository, never()).delete(any(Evento.class));
    }

    @Test
    void eliminarEvento_comoAdministrador_permiteEventoDeOtroOrganizador() {
        Evento evento = Evento.builder()
                .id(1L)
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(8L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        eventoService.eliminarEvento(1L, 7L, true);

        verify(eventoRepository).delete(evento);
    }

    @Test
    void eliminarEvento_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.eliminarEvento(99L, 7L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Evento no encontrado con id: 99");
    }
}
