package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.config.ReservaProperties;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.AsientoDTO;
import org.tudai.ticketya.tickets.dto.ReservaRequestDTO;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private DisponibilidadSectorRepository sectorRepository;

    @Mock
    private DisponibilidadAsientoRepository asientoRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ReservaVencidaService reservaVencidaService;

    @Mock
    private ReservaRepository reservaRepository;

    private ReservaProperties properties;

    private ReservaService service;

    @BeforeEach
    void setUp() {
        properties = new ReservaProperties();
        properties.setLimitePorCliente(4);
        properties.setExpiracion(Duration.ofMinutes(10));
        service = new ReservaService(
                eventoRepository, sectorRepository, asientoRepository, ticketRepository,
                reservaVencidaService, properties, reservaRepository);

        lenient().when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> {
            Reserva reserva = invocacion.getArgument(0);
            reserva.setId(99L);
            return reserva;
        });
    }

    @Test
    void reservar_cuandoSuperaElLimite_lanzaConflictException() {
        DisponibilidadEvento evento = evento(7L, 2);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 10);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(1L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.reservar(7L, 42L, reserva(3L, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("limite")
                .hasMessageContaining("2");

        verify(ticketRepository, never()).saveAll(any());
        assertThat(sector.getDisponibles()).isEqualTo(10);
    }

    @Test
    void reservar_sinLimiteConfigurado_usaElValorPorDefecto() {
        properties.setLimitePorCliente(3);
        DisponibilidadEvento evento = evento(7L, null);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 10);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(2L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        ReservaResponseDTO respuesta = service.reservar(7L, 42L, reserva(3L, 1));

        assertThat(respuesta.getLimiteEntradasPorCliente()).isEqualTo(3);
        assertThat(respuesta.getEntradasVigentes()).isEqualTo(3);
        assertThat(respuesta.getCantidad()).isEqualTo(1);
        assertThat(respuesta.getIdReserva()).isEqualTo(99L);
    }

    @Test
    void reservar_soloContabilizaVendidasYReservasVigentes() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 10);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(2L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(1L);

        ReservaResponseDTO respuesta = service.reservar(7L, 42L, reserva(3L, 2));

        assertThat(respuesta.getEntradasVigentes()).isEqualTo(5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EstadoTicket>> estados = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), estados.capture());
        assertThat(estados.getValue())
                .containsExactlyInAnyOrder(EstadoTicket.EMITIDO, EstadoTicket.USADO)
                .doesNotContain(EstadoTicket.DEVUELTO, EstadoTicket.CANCELADO,
                        EstadoTicket.EXPIRADO, EstadoTicket.LIBERADO);
    }

    @Test
    void reservar_usaBloqueoPesimistaAntesDeVerificarElLimite() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 10);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        service.reservar(7L, 42L, reserva(3L, 1));

        InOrder orden = inOrder(eventoRepository, ticketRepository);
        orden.verify(eventoRepository).findByIdForUpdate(7L);
        orden.verify(ticketRepository).countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any());
    }

    @Test
    void reservar_sectorNumerado_marcaLosPrimerosAsientosLibresComoReservados() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 3, 3);
        DisponibilidadAsiento primero = asiento(1, 1);
        DisponibilidadAsiento segundo = asiento(1, 2);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(asientoRepository.findBySector_IdAndEstadoOrderByFilaAscNumeroAsc(
                10L, EstadoDisponibilidad.LIBRE)).thenReturn(List.of(primero, segundo));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        ReservaResponseDTO respuesta = service.reservar(7L, 42L, reserva(3L, 2));

        assertThat(respuesta.getCantidad()).isEqualTo(2);
        assertThat(primero.getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(segundo.getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(sector.getDisponibles()).isEqualTo(1);
        assertThat(respuesta.getAsientos()).hasSize(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> tickets = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(tickets.capture());
        assertThat(tickets.getValue())
                .allSatisfy(ticket -> {
                    assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.RESERVADO);
                    assertThat(ticket.getClienteId()).isEqualTo(42L);
                    assertThat(ticket.getExpiraEl()).isAfter(Instant.now());
                    assertThat(ticket.getReserva().getId()).isEqualTo(99L);
                });
    }

    @Test
    void reservar_asientosEspecificos_losReservaYDevuelveIdYExpiracionDeDiezMinutos() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 3, 3);
        DisponibilidadAsiento primero = asiento(1, 1);
        DisponibilidadAsiento segundo = asiento(1, 2);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(asientoRepository.findBySector_IdAndFilaAndNumero(10L, 1, 1))
                .thenReturn(Optional.of(primero));
        when(asientoRepository.findBySector_IdAndFilaAndNumero(10L, 1, 2))
                .thenReturn(Optional.of(segundo));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        Instant antes = Instant.now();
        ReservaResponseDTO respuesta = service.reservar(7L, 42L, reservaAsientos(
                3L, asientoDto(1, 2), asientoDto(1, 1)));
        Instant despues = Instant.now();

        assertThat(respuesta.getIdReserva()).isEqualTo(99L);
        assertThat(respuesta.getCantidad()).isEqualTo(2);
        assertThat(respuesta.getExpiraEl()).isBetween(
                antes.plus(Duration.ofMinutes(9)), despues.plus(Duration.ofMinutes(11)));
        assertThat(primero.getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(segundo.getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(sector.getDisponibles()).isEqualTo(1);
        assertThat(respuesta.getAsientos())
                .extracting(asiento -> asiento.getFila() + "-" + asiento.getNumero())
                .containsExactly("1-1", "1-2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> tickets = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(tickets.capture());
        assertThat(tickets.getValue()).hasSize(2);
        assertThat(tickets.getValue())
                .extracting(ticket -> ticket.getAsiento().getFila()
                        + "-" + ticket.getAsiento().getNumero())
                .containsExactly("1-1", "1-2");
    }

    @Test
    void reservar_cuandoUnAsientoNoEstaLibre_noReservaNingunoYRespondeConflicto() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 3, 3);
        DisponibilidadAsiento libre = asiento(1, 1);
        DisponibilidadAsiento ocupado = asiento(1, 2);
        ocupado.setEstado(EstadoDisponibilidad.RESERVADO);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(asientoRepository.findBySector_IdAndFilaAndNumero(10L, 1, 1))
                .thenReturn(Optional.of(libre));
        when(asientoRepository.findBySector_IdAndFilaAndNumero(10L, 1, 2))
                .thenReturn(Optional.of(ocupado));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.reservar(7L, 42L, reservaAsientos(
                3L, asientoDto(1, 1), asientoDto(1, 2))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está disponible");

        assertThat(libre.getEstado()).isEqualTo(EstadoDisponibilidad.LIBRE);
        assertThat(ocupado.getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(sector.getDisponibles()).isEqualTo(3);
        verify(ticketRepository, never()).saveAll(any());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void reservar_cuandoElEventoNoEstaPublicado_lanzaConflictException() {
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservar(7L, 42L, reservaAsientos(
                3L, asientoDto(1, 1))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está publicado");

        verifyNoInteractions(sectorRepository, asientoRepository, ticketRepository,
                reservaRepository);
    }

    @Test
    void reservar_cuandoFallaLaRestriccionUnicaDeAsiento_lanzaConflictException() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 3, 3);
        DisponibilidadAsiento asiento = asiento(1, 1);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(asientoRepository.findBySector_IdAndFilaAndNumero(10L, 1, 1))
                .thenReturn(Optional.of(asiento));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);
        doThrow(new DataIntegrityViolationException("uq_tickets_asiento"))
                .when(ticketRepository).flush();

        assertThatThrownBy(() -> service.reservar(7L, 42L, reservaAsientos(
                3L, asientoDto(1, 1))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya no está disponible");
    }

    @Test
    void reservar_asientosEspecificos_enSectorNoNumerado_lanzaBadRequestException() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 10);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.reservar(7L, 42L, reservaAsientos(
                3L, asientoDto(1, 1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no es numerado");

        verifyNoInteractions(asientoRepository, reservaRepository);
    }

    @Test
    void reservar_sectorNoNumerado_descuentaLosDisponibles() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 8);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        service.reservar(7L, 42L, reserva(3L, 3));

        assertThat(sector.getDisponibles()).isEqualTo(5);
        verifyNoInteractions(asientoRepository);
    }

    @Test
    void reservar_sectorNoNumerado_sinDisponiblesSuficientes_lanzaConflictYNoModifica() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 2);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.reservar(7L, 42L, reserva(3L, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No hay entradas suficientes");

        assertThat(sector.getDisponibles()).isEqualTo(2);
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void reservar_sectorNoNumerado_ultimoLugar_noQuedaNegativo() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 1);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        service.reservar(7L, 42L, reserva(3L, 1));
        assertThat(sector.getDisponibles()).isZero();

        assertThatThrownBy(() -> service.reservar(7L, 43L, reserva(3L, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No hay entradas suficientes");

        assertThat(sector.getDisponibles()).isZero();
    }

    @Test
    void reservar_liberaLasReservasVencidasAntesDeContabilizar() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NO_NUMERADO, 10, 10);
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        service.reservar(7L, 42L, reserva(3L, 1));

        InOrder orden = inOrder(reservaVencidaService, ticketRepository);
        orden.verify(reservaVencidaService).liberarVencidas(eq(7L), any());
        orden.verify(ticketRepository).countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any());
    }

    @Test
    void reservar_conCantidadInvalida_lanzaBadRequestException() {
        assertThatThrownBy(() -> service.reservar(7L, 42L, reserva(3L, 0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cantidad");

        verifyNoInteractions(eventoRepository, sectorRepository, asientoRepository,
                ticketRepository, reservaRepository);
    }

    private DisponibilidadEvento evento(Long eventoId, Integer limite) {
        return DisponibilidadEvento.builder()
                .eventoId(eventoId)
                .limiteEntradasPorCliente(limite)
                .creadoEl(Instant.now())
                .build();
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

    private DisponibilidadAsiento asiento(int fila, int numero) {
        return DisponibilidadAsiento.builder()
                .fila(fila)
                .numero(numero)
                .estado(EstadoDisponibilidad.LIBRE)
                .build();
    }

    private ReservaRequestDTO reserva(Long sectorId, int cantidad) {
        return ReservaRequestDTO.builder()
                .sectorId(sectorId)
                .cantidad(cantidad)
                .build();
    }

    private ReservaRequestDTO reservaAsientos(Long sectorId, AsientoDTO... asientos) {
        return ReservaRequestDTO.builder()
                .sectorId(sectorId)
                .asientos(List.of(asientos))
                .build();
    }

    private AsientoDTO asientoDto(int fila, int numero) {
        return AsientoDTO.builder()
                .fila(fila)
                .numero(numero)
                .build();
    }
}
