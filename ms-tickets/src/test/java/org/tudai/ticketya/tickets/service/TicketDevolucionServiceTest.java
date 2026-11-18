package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadAsiento;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.dto.DevolucionTicketResponseDTO;
import org.tudai.ticketya.tickets.dto.EntradaDetalleDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketDevolucionServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TransaccionesApiClient transaccionesClient;

    private TicketDevolucionService service;

    @BeforeEach
    void setUp() {
        service = new TicketDevolucionService(ticketRepository, transaccionesClient);
    }

    @Test
    @DisplayName("Aprobar devolución: ticket pasa a DEVUELTO, asiento queda LIBRE y cupo del sector vuelve a estar disponible")
    void devolverTicket_pasaADevuelto_liberaAsientoYCupo() {
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(10L)
                .organizadorId(1L)
                .build();

        DisponibilidadSector sector = DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(100L)
                .tipo(TipoSector.NUMERADO)
                .capacidadTotal(50)
                .disponibles(10)
                .build();

        DisponibilidadAsiento asiento = DisponibilidadAsiento.builder()
                .id(5L)
                .sector(sector)
                .estado(EstadoDisponibilidad.VENDIDO)
                .fila(1)
                .numero(10)
                .build();

        Ticket ticket = Ticket.builder()
                .id(100L)
                .evento(evento)
                .sector(sector)
                .asiento(asiento)
                .clienteId(20L)
                .precio(1500.0)
                .estadoTicket(EstadoTicket.EMITIDO)
                .build();

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenReturn(new TransaccionResponse().id(999L).estado(EstadoTransaccion.COMPLETA));

        DevolucionTicketResponseDTO respuesta = service.devolverTicket(100L);

        assertThat(respuesta.getTicketId()).isEqualTo(100L);
        assertThat(respuesta.getEstado()).isEqualTo(EstadoTicket.DEVUELTO.name());
        assertThat(respuesta.getTransaccionId()).isEqualTo(999L);
        assertThat(respuesta.getReintegroPendiente()).isFalse();

        assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.DEVUELTO);
        assertThat(ticket.getAsiento()).isNull();
        assertThat(asiento.getEstado()).isEqualTo(EstadoDisponibilidad.LIBRE);
        assertThat(sector.getDisponibles()).isEqualTo(11);

        verify(ticketRepository).saveAndFlush(ticket);
    }

    @Test
    @DisplayName("Aprobar devolución en sector no numerado: incrementa disponibles")
    void devolverTicket_sectorNoNumerado_incrementaDisponibles() {
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(10L)
                .organizadorId(1L)
                .build();

        DisponibilidadSector sector = DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(200L)
                .tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(100)
                .disponibles(40)
                .build();

        Ticket ticket = Ticket.builder()
                .id(101L)
                .evento(evento)
                .sector(sector)
                .asiento(null)
                .clienteId(20L)
                .precio(1000.0)
                .estadoTicket(EstadoTicket.VENDIDO)
                .build();

        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenReturn(new TransaccionResponse().id(888L).estado(EstadoTransaccion.COMPLETA));

        DevolucionTicketResponseDTO respuesta = service.devolverTicket(101L);

        assertThat(respuesta.getEstado()).isEqualTo("DEVUELTO");
        assertThat(respuesta.getTransaccionId()).isEqualTo(888L);
        assertThat(respuesta.getReintegroPendiente()).isFalse();
        assertThat(sector.getDisponibles()).isEqualTo(41);
    }

    @Test
    @DisplayName("Falla el reintegro en la pasarela (FALLIDA): devolución queda aprobada con reintegro pendiente")
    void devolverTicket_reintegroFallaEnPasarela_quedaAprobadaConReintegroPendiente() {
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(10L)
                .organizadorId(1L)
                .build();

        DisponibilidadSector sector = DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(100L)
                .tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(50)
                .disponibles(10)
                .build();

        Ticket ticket = Ticket.builder()
                .id(102L)
                .evento(evento)
                .sector(sector)
                .clienteId(20L)
                .precio(1500.0)
                .estadoTicket(EstadoTicket.VENDIDO)
                .build();

        when(ticketRepository.findById(102L)).thenReturn(Optional.of(ticket));
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenReturn(new TransaccionResponse().id(777L).estado(EstadoTransaccion.FALLIDA));

        DevolucionTicketResponseDTO respuesta = service.devolverTicket(102L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoTicket.DEVUELTO.name());
        assertThat(respuesta.getTransaccionId()).isEqualTo(777L);
        assertThat(respuesta.getReintegroPendiente()).isTrue();
        assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.DEVUELTO);
        assertThat(sector.getDisponibles()).isEqualTo(11);
    }

    @Test
    @DisplayName("Pasarela lanza excepción: devolución queda aprobada con reintegro pendiente")
    void devolverTicket_pasarelaLanzaExcepcion_quedaAprobadaConReintegroPendiente() {
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(10L)
                .organizadorId(1L)
                .build();

        DisponibilidadSector sector = DisponibilidadSector.builder()
                .evento(evento)
                .sectorIdExterno(100L)
                .tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(50)
                .disponibles(10)
                .build();

        Ticket ticket = Ticket.builder()
                .id(103L)
                .evento(evento)
                .sector(sector)
                .clienteId(20L)
                .precio(1500.0)
                .estadoTicket(EstadoTicket.VENDIDO)
                .build();

        when(ticketRepository.findById(103L)).thenReturn(Optional.of(ticket));
        when(transaccionesClient.generarCredito(any(GenerarTransaccionRequest.class)))
                .thenThrow(new RuntimeException("Timeout de pasarela"));

        DevolucionTicketResponseDTO respuesta = service.devolverTicket(103L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoTicket.DEVUELTO.name());
        assertThat(respuesta.getReintegroPendiente()).isTrue();
        assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.DEVUELTO);
    }

    @Test
    @DisplayName("Ticket ya devuelto: lanza ConflictException (409)")
    void devolverTicket_ticketYaDevuelto_lanzaConflictException() {
        Ticket ticket = Ticket.builder()
                .id(104L)
                .estadoTicket(EstadoTicket.DEVUELTO)
                .build();

        when(ticketRepository.findById(104L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.devolverTicket(104L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue devuelto");
    }

    @Test
    @DisplayName("Ticket ya utilizado: lanza ConflictException (409)")
    void devolverTicket_ticketYaUsado_lanzaConflictException() {
        Ticket ticket = Ticket.builder()
                .id(105L)
                .estadoTicket(EstadoTicket.USADO)
                .build();

        when(ticketRepository.findById(105L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.devolverTicket(105L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue utilizado");
    }

    @Test
    @DisplayName("Ticket no existe: lanza NotFoundException (404)")
    void devolverTicket_ticketNoExiste_lanzaNotFoundException() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.devolverTicket(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No existe el ticket");
    }

    @Test
    @DisplayName("Obtener detalle de entrada")
    void obtenerDetalle_retornaEntrada() {
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(10L)
                .build();

        DisponibilidadSector sector = DisponibilidadSector.builder()
                .sectorIdExterno(100L)
                .build();

        Ticket ticket = Ticket.builder()
                .id(100L)
                .evento(evento)
                .sector(sector)
                .clienteId(20L)
                .precio(1500.0)
                .estadoTicket(EstadoTicket.EMITIDO)
                .build();

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        EntradaDetalleDTO detalle = service.obtenerDetalle(100L);

        assertThat(detalle.getId()).isEqualTo(100L);
        assertThat(detalle.getEventoId()).isEqualTo(10L);
        assertThat(detalle.getClienteId()).isEqualTo(20L);
        assertThat(detalle.getPrecio()).isEqualTo(1500.0);
        assertThat(detalle.getEstado()).isEqualTo("EMITIDO");
        assertThat(detalle.getUtilizado()).isFalse();
    }
}
