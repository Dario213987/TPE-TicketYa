package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.config.ReservaProperties;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.ReservaRequestDTO;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

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

    private ReservaProperties properties;

    private ReservaService service;

    @BeforeEach
    void setUp() {
        properties = new ReservaProperties();
        properties.setLimitePorCliente(4);
        service = new ReservaService(
                eventoRepository, sectorRepository, asientoRepository, ticketRepository, properties);
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
                .doesNotContain(EstadoTicket.DEVUELTO, EstadoTicket.CANCELADO, EstadoTicket.EXPIRADO);
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> tickets = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(tickets.capture());
        assertThat(tickets.getValue())
                .allSatisfy(ticket -> {
                    assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.RESERVADO);
                    assertThat(ticket.getClienteId()).isEqualTo(42L);
                    assertThat(ticket.getExpiraEl()).isAfter(Instant.now());
                });
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
    void reservar_liberaLasReservasVencidasAntesDeContabilizar() {
        DisponibilidadEvento evento = evento(7L, 5);
        DisponibilidadSector sector = sector(10L, 3L, TipoSector.NUMERADO, 3, 2);
        DisponibilidadAsiento asiento = asiento(1, 1);
        asiento.setEstado(EstadoDisponibilidad.RESERVADO);
        Ticket vencida = Ticket.builder()
                .estadoTicket(EstadoTicket.RESERVADO)
                .expiraEl(Instant.now().minusSeconds(60))
                .evento(evento)
                .sector(sector)
                .asiento(asiento)
                .build();
        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(ticketRepository.findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
                eq(7L), eq(EstadoTicket.RESERVADO), any())).thenReturn(List.of(vencida));
        when(sectorRepository.findByEvento_EventoIdAndSectorIdExterno(7L, 3L))
                .thenReturn(Optional.of(sector));
        when(asientoRepository.findBySector_IdAndEstadoOrderByFilaAscNumeroAsc(
                10L, EstadoDisponibilidad.LIBRE)).thenReturn(List.of(asiento));
        when(ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eq(7L), eq(42L), any())).thenReturn(0L);
        when(ticketRepository.contarReservasVigentes(eq(7L), eq(42L), eq(EstadoTicket.RESERVADO), any()))
                .thenReturn(0L);

        service.reservar(7L, 42L, reserva(3L, 1));

        assertThat(vencida.getEstadoTicket()).isEqualTo(EstadoTicket.EXPIRADO);
        assertThat(asiento.getEstado()).isEqualTo(EstadoDisponibilidad.RESERVADO);
        assertThat(sector.getDisponibles()).isEqualTo(2);
        verify(ticketRepository).saveAll(argThat(tickets -> {
            List<Ticket> lista = new java.util.ArrayList<>();
            tickets.forEach(lista::add);
            return lista.contains(vencida);
        }));
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
}
