package org.tudai.ticketya.eventos.service;

import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoBorradorCreateRequest;
import org.tudai.ticketya.contract.eventos.model.EventoEstadoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoUpdateRequest;
import org.tudai.ticketya.contract.eventos.model.ParticipanteRequest;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorResponse;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.client.dto.AsientoRequest;
import org.tudai.ticketya.eventos.client.dto.PublicarEventoRequest;
import org.tudai.ticketya.eventos.client.dto.SectorDisponibilidadRequest;
import org.tudai.ticketya.eventos.client.dto.TipoSectorRequest;
import org.tudai.ticketya.eventos.config.ModelMapperConfig;
import org.tudai.ticketya.eventos.domain.Asiento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.exception.FechaEventoInvalidaException;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private LugarRepository lugarRepository;

    @Mock
    private TicketsClient ticketsClient;

    @Mock
    private LugarService lugarService;

    @Mock
    private ParticipanteService participanteService;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    private TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private EventoService eventoService;

    @Test
    void losEventosAgotadosSiguenSiendoVisibles() {
        assertThat(EventoService.ESTADOS_VISIBLES)
                .containsExactlyInAnyOrder(EstadoEvento.PUBLICADO, EstadoEvento.CONFIRMADO,
                        EstadoEvento.FINALIZADO, EstadoEvento.AGOTADO)
                .doesNotContain(EstadoEvento.BORRADOR, EstadoEvento.CANCELADO, EstadoEvento.SUSPENDIDO);
    }

    @Test
    void listarPublicados_consultaLosEstadosVisibles() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Evento> page = new PageImpl<>(List.of(Evento.builder().id(1L).build()));
        when(eventoRepository.findByEstadoIn(EventoService.ESTADOS_VISIBLES, pageable)).thenReturn(page);

        assertThat(eventoService.listarPublicados(pageable)).isSameAs(page);
        verify(eventoRepository).findByEstadoIn(EventoService.ESTADOS_VISIBLES, pageable);
    }

    @Test
    void crearEventoBorrador_guardaBorradorDelOrganizadorConParticipantesExistentesYNuevos() {
        Lugar lugar = Lugar.builder().id(10L).nombre("Estadio").build();
        Participante existente = Participante.builder().id(3L).nombre("Artista existente").build();
        Participante nuevo = Participante.builder().nombre("Artista nuevo").build();
        when(lugarService.obtenerPorId(10L)).thenReturn(lugar);
        when(participanteService.obtenerPorId(3L)).thenReturn(existente);
        when(participanteService.crearParticipante(any(ParticipanteRequest.class))).thenReturn(nuevo);
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> {
            Evento evento = invocation.getArgument(0);
            evento.setId(20L);
            return evento;
        });
        EventoBorradorCreateRequest request = new EventoBorradorCreateRequest(
                "Recital", "Presentación en vivo", TipoEvento.RECITAL, LocalDate.now().plusDays(5), 10L)
                .addParticipantesItem(new ParticipanteRequest().id(3L))
                .addParticipantesItem(new ParticipanteRequest().nombre("Artista nuevo"));

        EventoResponse response = eventoService.crearEventoBorrador(request, 7L);

        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.BORRADOR);
        assertThat(response.getOrganizadorId()).isEqualTo(7L);
        assertThat(response.getParticipantes()).hasSize(2);
        verify(eventoRepository).save(any(Evento.class));
    }

    @Test
    void crearEventoBorrador_conFechaPasada_rechazaSinGuardar() {
        EventoBorradorCreateRequest request = new EventoBorradorCreateRequest(
                "Recital", "Presentación en vivo", TipoEvento.RECITAL, LocalDate.now().minusDays(1), 10L);

        assertThatThrownBy(() -> eventoService.crearEventoBorrador(request, 7L))
                .isInstanceOf(FechaEventoInvalidaException.class);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void crearEventoBorrador_conLugarInexistente_propagaNotFound() {
        when(lugarService.obtenerPorId(999L)).thenThrow(new NotFoundException("Lugar no encontrado"));
        EventoBorradorCreateRequest request = new EventoBorradorCreateRequest(
                "Recital", "Presentación en vivo", TipoEvento.RECITAL, LocalDate.now().plusDays(1), 999L);

        assertThatThrownBy(() -> eventoService.crearEventoBorrador(request, 7L))
                .isInstanceOf(NotFoundException.class);
        verify(eventoRepository, never()).save(any(Evento.class));
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

        PrecioEventoSectorResponse response =
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
                .tipo(TipoEvento.RECITAL)
                .fecha(LocalDate.of(2026, 12, 1))
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoUpdateRequest dto = new EventoUpdateRequest()
                .nombre("Nombre nuevo")
                .descripcion("Descripción nueva")
                .tipo(TipoEvento.TEATRO);

        EventoResponse response = eventoService.actualizarEvento(1L, dto, 7L, false);

        assertThat(evento.getNombre()).isEqualTo("Nombre nuevo");
        assertThat(evento.getDescripcion()).isEqualTo("Descripción nueva");
        assertThat(evento.getTipo()).isEqualTo(TipoEvento.TEATRO);
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

        EventoUpdateRequest dto = new EventoUpdateRequest()
                .fecha(LocalDate.of(2026, 12, 15))
                .lugarId(20L);

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

        EventoUpdateRequest dto = new EventoUpdateRequest().fecha(LocalDate.of(2026, 12, 15));

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

        EventoUpdateRequest dto = new EventoUpdateRequest().nombre("Nombre nuevo");

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

        EventoUpdateRequest dto = new EventoUpdateRequest().nombre("Nombre nuevo");

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

        eventoService.actualizarEvento(1L, new EventoUpdateRequest().nombre("Nombre nuevo"), 7L, true);

        assertThat(evento.getNombre()).isEqualTo("Nombre nuevo");
    }

    @Test
    void actualizarEvento_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.actualizarEvento(
                99L, new EventoUpdateRequest().nombre("Nombre"), 7L, false))
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

    @Test
    void publicarEvento_enBorradorPropio_conPreciosDelEvento_publicaYEnviaDisponibilidad() {
        Evento evento = eventoPublicable();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        EventoEstadoResponse response = eventoService.publicarEvento(1L, 7L, false);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(ticketsClient).crearDisponibilidad(eq(1L), any(PublicarEventoRequest.class));
        verify(eventoRepository).save(evento);
    }

    @Test
    void publicarEvento_armaElInventarioOrdenadoPorSectorYAsiento() {
        SectorNumerado sector30 = sectorNumerado(30L, "Pullman", null, asiento(1, 1));
        SectorNoNumerado sector20 = sectorNoNumerado(20L, "Campo", null, 100);
        SectorNumerado sector10 = sectorNumerado(10L, "Platea", null,
                asiento(2, 1), asiento(1, 2), asiento(1, 1));
        Evento evento = eventoBorrador(lugarConSectores(sector30, sector20, sector10));
        evento.definirPrecioSector(10L, 1000.0);
        evento.definirPrecioSector(20L, 2000.0);
        evento.definirPrecioSector(30L, 3000.0);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        eventoService.publicarEvento(1L, 7L, false);

        ArgumentCaptor<PublicarEventoRequest> captor = ArgumentCaptor.forClass(PublicarEventoRequest.class);
        verify(ticketsClient).crearDisponibilidad(eq(1L), captor.capture());
        PublicarEventoRequest request = captor.getValue();

        assertThat(request.getEventoId()).isEqualTo(1L);
        assertThat(request.getOrganizadorId()).isEqualTo(7L);
        assertThat(request.getSectores())
                .extracting(SectorDisponibilidadRequest::getSectorId, SectorDisponibilidadRequest::getPrecio)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 1000.0),
                        org.assertj.core.groups.Tuple.tuple(20L, 2000.0),
                        org.assertj.core.groups.Tuple.tuple(30L, 3000.0));

        SectorDisponibilidadRequest platea = request.getSectores().get(0);
        assertThat(platea.getTipo()).isEqualTo(TipoSectorRequest.NUMERADO);
        assertThat(platea.getAsientos()).containsExactly(
                AsientoRequest.builder().fila(1).numero(1).build(),
                AsientoRequest.builder().fila(1).numero(2).build(),
                AsientoRequest.builder().fila(2).numero(1).build());

        SectorDisponibilidadRequest campo = request.getSectores().get(1);
        assertThat(campo.getTipo()).isEqualTo(TipoSectorRequest.NO_NUMERADO);
        assertThat(campo.getCapacidadMaxima()).isEqualTo(100);
        assertThat(campo.getAsientos()).isNull();
    }

    @Test
    void publicarEvento_yaPublicado_esIdempotente() {
        Evento evento = Evento.builder()
                .id(1L)
                .nombre("Festival")
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(7L)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        EventoEstadoResponse response = eventoService.publicarEvento(1L, 7L, false);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.publicarEvento(99L, 7L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Evento 99 no existe");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_enEventoDeOtroOrganizador_lanzaForbidden() {
        Evento evento = eventoPublicable();
        evento.setOrganizadorId(8L);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("No podés publicar un evento que no te pertenece");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_comoAdministrador_permiteEventoDeOtroOrganizador() {
        Evento evento = eventoPublicable();
        evento.setOrganizadorId(8L);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        eventoService.publicarEvento(1L, 7L, true);

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(ticketsClient).crearDisponibilidad(eq(1L), any(PublicarEventoRequest.class));
        verify(eventoRepository).save(evento);
    }

    @Test
    void publicarEvento_enEstadoNoBorrador_lanzaConflict() {
        for (EstadoEvento estado : List.of(
                EstadoEvento.CONFIRMADO,
                EstadoEvento.CANCELADO,
                EstadoEvento.FINALIZADO,
                EstadoEvento.AGOTADO)) {
            Evento evento = eventoPublicable();
            evento.setEstado(estado);
            when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Solo se pueden publicar eventos en estado BORRADOR");
        }
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_conFechaNula_lanzaBadRequest() {
        Evento evento = eventoPublicable();
        evento.setFecha(null);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La fecha del evento debe ser posterior a hoy");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_conFechaDeHoy_lanzaBadRequest() {
        Evento evento = eventoPublicable();
        evento.setFecha(LocalDate.now());
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La fecha del evento debe ser posterior a hoy");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_sinLugar_lanzaBadRequest() {
        Evento evento = eventoPublicable();
        evento.setLugar(null);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El evento no tiene un lugar asociado");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_lugarSinSectores_lanzaBadRequest() {
        Evento evento = eventoBorrador(Lugar.builder().id(10L).nombre("Estadio vacío").build());
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El lugar del evento no tiene sectores");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_conSectoresSinPrecioDelEvento_lanzaSectoresSinPrecioOrdenados() {
        SectorNumerado sector20 = sectorNumerado(20L, "Campo", 999.0);
        SectorNumerado sector10 = sectorNumerado(10L, "Platea", null);
        Evento evento = eventoBorrador(lugarConSectores(sector20, sector10));
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(SectoresSinPrecioException.class)
                .satisfies(ex -> {
                    SectoresSinPrecioException sinPrecio = (SectoresSinPrecioException) ex;
                    assertThat(sinPrecio.getSectores())
                            .extracting(SectoresSinPrecioException.SectorFaltante::id)
                            .containsExactly(10L, 20L);
                    assertThat(sinPrecio.nombres()).isEqualTo("Platea, Campo");
                });
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void publicarEvento_conMsTicketsNoDisponible_lanzaRemoteServiceExceptionYNoGuarda() {
        Evento evento = eventoPublicable();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(ticketsClient.crearDisponibilidad(eq(1L), any(PublicarEventoRequest.class)))
                .thenThrow(mock(RetryableException.class));

        assertThatThrownBy(() -> eventoService.publicarEvento(1L, 7L, false))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets no disponible")
                .satisfies(ex -> assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(502));
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.BORRADOR);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void cancelarEvento_publicadoComoAdmin_loCancelaYBloqueaTickets() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoEstadoResponse response = eventoService.cancelarEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.CANCELADO);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.CANCELADO);

        InOrder orden = inOrder(eventoRepository, ticketsClient);
        orden.verify(ticketsClient).cancelarEvento(1L);
        orden.verify(eventoRepository).save(evento);
    }

    @Test
    void cancelarEvento_conEventoInexistente_lanzaNotFoundYNoAvisaATickets() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.cancelarEvento(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontró el evento solicitado");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void cancelarEvento_yaCancelado_esIdempotenteYNoAvisaATickets() {
        Evento evento = eventoCancelable(EstadoEvento.CANCELADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        EventoEstadoResponse response = eventoService.cancelarEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.CANCELADO);
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void cancelarEvento_enEstadoNoCancelable_lanzaConflictYNoAvisaATickets() {
        for (EstadoEvento estado : List.of(
                EstadoEvento.BORRADOR,
                EstadoEvento.CONFIRMADO,
                EstadoEvento.FINALIZADO,
                EstadoEvento.AGOTADO)) {
            Evento evento = eventoCancelable(estado);
            when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> eventoService.cancelarEvento(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("El evento debe ser publicado para poder ser cancelado");
        }
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void cancelarEvento_conMsTicketsNoDisponible_lanzaRemoteServiceException() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        doThrow(mock(RetryableException.class)).when(ticketsClient).cancelarEvento(1L);

        assertThatThrownBy(() -> eventoService.cancelarEvento(1L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets no disponible")
                .satisfies(ex -> assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(502));

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void cancelarEvento_cuandoMsTicketsRespondeConError_propagaElStatus() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(409);
        doThrow(error).when(ticketsClient).cancelarEvento(1L);

        assertThatThrownBy(() -> eventoService.cancelarEvento(1L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets respondió con error")
                .satisfies(ex -> assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(409));

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void cancelarEvento_desdeSuspendido_loCancelaYNotificaTickets() {
        Evento evento = eventoCancelable(EstadoEvento.SUSPENDIDO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoEstadoResponse response = eventoService.cancelarEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.CANCELADO);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.CANCELADO);

        InOrder orden = inOrder(eventoRepository, ticketsClient);
        orden.verify(ticketsClient).cancelarEvento(1L);
        orden.verify(eventoRepository).save(evento);
    }

    @Test
    void suspenderEvento_publicadoComoAdmin_loSuspendeYBloqueaTickets() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoEstadoResponse response = eventoService.suspenderEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.SUSPENDIDO);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.SUSPENDIDO);

        InOrder orden = inOrder(eventoRepository, ticketsClient);
        orden.verify(ticketsClient).suspenderEvento(1L);
        orden.verify(eventoRepository).save(evento);
    }

    @Test
    void suspenderEvento_conEventoInexistente_lanzaNotFoundYNoAvisaATickets() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.suspenderEvento(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontró el evento solicitado");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void suspenderEvento_yaSuspendido_esIdempotenteYNoAvisaATickets() {
        Evento evento = eventoCancelable(EstadoEvento.SUSPENDIDO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        EventoEstadoResponse response = eventoService.suspenderEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.SUSPENDIDO);
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void suspenderEvento_enEstadoNoSuspendible_lanzaConflictYNoAvisaATickets() {
        for (EstadoEvento estado : List.of(
                EstadoEvento.BORRADOR,
                EstadoEvento.CONFIRMADO,
                EstadoEvento.CANCELADO,
                EstadoEvento.FINALIZADO,
                EstadoEvento.AGOTADO)) {
            Evento evento = eventoCancelable(estado);
            when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> eventoService.suspenderEvento(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("El evento debe ser publicado para poder ser suspendido");
        }
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void suspenderEvento_conMsTicketsNoDisponible_lanzaRemoteServiceException() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        doThrow(mock(RetryableException.class)).when(ticketsClient).suspenderEvento(1L);

        assertThatThrownBy(() -> eventoService.suspenderEvento(1L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets no disponible")
                .satisfies(ex -> assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(502));

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void suspenderEvento_cuandoMsTicketsRespondeConError_propagaElStatus() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(409);
        doThrow(error).when(ticketsClient).suspenderEvento(1L);

        assertThatThrownBy(() -> eventoService.suspenderEvento(1L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets respondió con error")
                .satisfies(ex -> assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(409));

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void reactivarEvento_suspendidoLoPublicaYNotificaTickets() {
        Evento evento = eventoCancelable(EstadoEvento.SUSPENDIDO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoEstadoResponse response = eventoService.reactivarEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);

        InOrder orden = inOrder(eventoRepository, ticketsClient);
        orden.verify(ticketsClient).reactivarEvento(1L);
        orden.verify(eventoRepository).save(evento);
    }

    @Test
    void reactivarEvento_yaPublicado_esIdempotenteYNoAvisaATickets() {
        Evento evento = eventoCancelable(EstadoEvento.PUBLICADO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        EventoEstadoResponse response = eventoService.reactivarEvento(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void reactivarEvento_conEventoInexistente_lanzaNotFoundYNoAvisaATickets() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.reactivarEvento(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No se encontró el evento solicitado");
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void reactivarEvento_enEstadoNoSuspendido_lanzaConflictYNoAvisaATickets() {
        for (EstadoEvento estado : List.of(
                EstadoEvento.BORRADOR,
                EstadoEvento.CONFIRMADO,
                EstadoEvento.CANCELADO,
                EstadoEvento.FINALIZADO,
                EstadoEvento.AGOTADO)) {
            Evento evento = eventoCancelable(estado);
            when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> eventoService.reactivarEvento(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("El evento debe estar suspendido para poder ser reactivado");
        }
        verifyNoInteractions(ticketsClient);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void reactivarEvento_conMsTicketsNoDisponible_lanzaRemoteServiceExceptionYNoGuarda() {
        Evento evento = eventoCancelable(EstadoEvento.SUSPENDIDO);
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        doThrow(mock(RetryableException.class)).when(ticketsClient).reactivarEvento(1L);

        assertThatThrownBy(() -> eventoService.reactivarEvento(1L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessage("ms-tickets no disponible")
                .satisfies(ex -> assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(502));

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.SUSPENDIDO);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    private Evento eventoCancelable(EstadoEvento estado) {
        return Evento.builder()
                .id(1L)
                .nombre("Festival")
                .estado(estado)
                .organizadorId(7L)
                .build();
    }

    private Evento eventoPublicable() {
        Evento evento = eventoBorrador(lugarConSectores(sectorNumerado(10L, "Platea", null, asiento(1, 1))));
        evento.definirPrecioSector(10L, 1000.0);
        return evento;
    }

    private Evento eventoBorrador(Lugar lugar) {
        return Evento.builder()
                .id(1L)
                .nombre("Festival")
                .fecha(LocalDate.now().plusDays(1))
                .estado(EstadoEvento.BORRADOR)
                .organizadorId(7L)
                .lugar(lugar)
                .build();
    }

    private Lugar lugarConSectores(Sector... sectores) {
        Lugar lugar = Lugar.builder()
                .id(10L)
                .nombre("Estadio")
                .build();
        for (Sector sector : sectores) {
            lugar.addSector(sector);
        }
        return lugar;
    }

    private SectorNumerado sectorNumerado(Long id, String nombre, Double precio, Asiento... asientos) {
        SectorNumerado sector = SectorNumerado.builder()
                .id(id)
                .nombre(nombre)
                .precio(precio)
                .build();
        for (Asiento asiento : asientos) {
            sector.addAsiento(asiento);
        }
        return sector;
    }

    private SectorNoNumerado sectorNoNumerado(Long id, String nombre, Double precio, Integer capacidadMaxima) {
        return SectorNoNumerado.builder()
                .id(id)
                .nombre(nombre)
                .precio(precio)
                .capacidadMaxima(capacidadMaxima)
                .build();
    }

    private Asiento asiento(Integer fila, Integer numero) {
        return Asiento.builder()
                .fila(fila)
                .numero(numero)
                .build();
    }
}
