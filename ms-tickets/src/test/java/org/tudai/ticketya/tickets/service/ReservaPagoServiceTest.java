package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Cobro;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Preparacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaPagoServiceTest {

    static final Long RESERVA_ID = 99L;
    static final Long EVENTO_ID = 7L;
    static final Long CLIENTE_ID = 42L;
    static final Long ORGANIZADOR_ID = 5L;
    static final String CLAVE = "clave-1";

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ReservaVencidaService reservaVencidaService;

    private ReservaPagoService service;

    @BeforeEach
    void setUp() {
        service = new ReservaPagoService(reservaRepository, eventoRepository, ticketRepository,
                reservaVencidaService);
    }

    @Test
    void preparar_reservaVigente_quedaPendienteDePagoYDevuelveLosCobros() {
        Reserva reserva = reserva(null);
        Ticket uno = ticket(1L, sector(1500.0), null, EstadoTicket.RESERVADO);
        Ticket dos = ticket(2L, sector(1500.0), null, EstadoTicket.RESERVADO);
        prepararReserva(reserva, evento(ORGANIZADOR_ID), List.of(uno, dos));

        Preparacion preparacion = service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA");

        assertThat(preparacion.yaTerminada()).isFalse();
        assertThat(preparacion.organizadorId()).isEqualTo(ORGANIZADOR_ID);
        assertThat(preparacion.cobros()).containsExactly(new Cobro(1L, 1500.0), new Cobro(2L, 1500.0));
        assertThat(List.of(uno, dos)).allSatisfy(ticket -> {
            assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.PENDIENTE_PAGO);
            assertThat(ticket.getPrecio()).isEqualTo(1500.0);
        });
        assertThat(reserva.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE_PAGO);
        assertThat(reserva.getIdempotencyKey()).isEqualTo(CLAVE);
        assertThat(reserva.getMedioPago()).isEqualTo("TARJETA");
        verify(reservaVencidaService).liberarVencidas(eq(EVENTO_ID), any(Instant.class));
    }

    @Test
    void preparar_reintentoDeUnPagoPendiente_devuelveLosMismosCobros() {
        Reserva reserva = reserva(EstadoPago.PENDIENTE_PAGO);
        Ticket pendiente = ticket(1L, sector(1500.0), null, EstadoTicket.PENDIENTE_PAGO);
        pendiente.setPrecio(1500.0);
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento(ORGANIZADOR_ID)));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(List.of(pendiente));

        Preparacion preparacion = service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA");

        assertThat(preparacion.cobros()).containsExactly(new Cobro(1L, 1500.0));
        verifyNoInteractions(reservaVencidaService);
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void preparar_reintentoDeUnPagoAprobado_devuelveLaCompraOriginal() {
        Reserva reserva = reserva(EstadoPago.APROBADO);
        Ticket vendido = ticket(1L, sector(1500.0), null, EstadoTicket.VENDIDO);
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(List.of(vendido));

        Preparacion preparacion = service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA");

        assertThat(preparacion.yaTerminada()).isTrue();
        assertThat(preparacion.compraExistente().getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
        verifyNoInteractions(eventoRepository);
    }

    @Test
    void preparar_reintentoDeUnPagoRechazadoOReintegrado_lanzaElMismoRechazo() {
        for (EstadoPago estado : List.of(EstadoPago.RECHAZADO, EstadoPago.REINTEGRADO,
                EstadoPago.REINTEGRO_PENDIENTE)) {
            Reserva reserva = reserva(estado);
            reserva.setMotivoRechazo("fondos insuficientes");
            when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));

            assertThatThrownBy(() -> service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                    .isInstanceOf(PagoRechazadoException.class)
                    .hasMessageContaining("fondos insuficientes");
        }
    }

    @Test
    void preparar_reservaProcesadaConOtraClave_lanzaConflictException() {
        Reserva reserva = reserva(EstadoPago.APROBADO);
        reserva.setIdempotencyKey("otra-clave");
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue procesada");
    }

    @Test
    void preparar_reservaDeOtroCliente_lanzaForbiddenException() {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva(null)));

        assertThatThrownBy(() -> service.preparar(RESERVA_ID, 1000L, CLAVE, "TARJETA"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void preparar_reservaInexistente_lanzaNotFoundException() {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void preparar_reservaVencida_lanzaConflictException() {
        prepararReserva(reserva(null), evento(ORGANIZADOR_ID),
                List.of(ticket(1L, sector(1500.0), null, EstadoTicket.EXPIRADO)));

        assertThatThrownBy(() -> service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("venció");
    }

    @Test
    void preparar_eventoSinOrganizador_lanzaConflictException() {
        prepararReserva(reserva(null), evento(null),
                List.of(ticket(1L, sector(1500.0), null, EstadoTicket.RESERVADO)));

        assertThatThrownBy(() -> service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("organizador");
    }

    @Test
    void preparar_sectorSinPrecio_lanzaConflictException() {
        prepararReserva(reserva(null), evento(ORGANIZADOR_ID),
                List.of(ticket(1L, sector(null), null, EstadoTicket.RESERVADO)));

        assertThatThrownBy(() -> service.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("precio");
    }

    @Test
    void emitir_pagoPendiente_emiteLosTicketsVendidos() {
        Reserva reserva = reserva(EstadoPago.PENDIENTE_PAGO);
        DisponibilidadSector sector = sector(1500.0);
        DisponibilidadAsiento asiento = DisponibilidadAsiento.builder()
                .sector(sector).fila(2).numero(5).estado(EstadoDisponibilidad.RESERVADO).build();
        Ticket ticket = ticket(1L, sector, asiento, EstadoTicket.PENDIENTE_PAGO);
        ticket.setPrecio(1500.0);
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(List.of(ticket));

        CompraResponseDTO compra = service.emitir(RESERVA_ID);

        assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.VENDIDO);
        assertThat(ticket.getExpiraEl()).isNull();
        assertThat(asiento.getEstado()).isEqualTo(EstadoDisponibilidad.VENDIDO);
        assertThat(reserva.getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(reserva.getPagadoEl()).isNotNull();
        assertThat(compra.getTotal()).isEqualTo(1500.0);
        assertThat(compra.getTickets()).singleElement().satisfies(emitido -> {
            assertThat(emitido.getCodigoQr()).isEqualTo("qr-1");
            assertThat(emitido.getFila()).isEqualTo(2);
            assertThat(emitido.getNumero()).isEqualTo(5);
        });
    }

    @Test
    void emitir_reservaSinPagoPendiente_lanzaConflictException() {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID))
                .thenReturn(Optional.of(reserva(EstadoPago.RECHAZADO)));

        assertThatThrownBy(() -> service.emitir(RESERVA_ID))
                .isInstanceOf(ConflictException.class);
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void liberar_pagoPendiente_liberaLosTicketsYGuardaElEstadoFinal() {
        Reserva reserva = reserva(EstadoPago.PENDIENTE_PAGO);
        Ticket pendiente = ticket(1L, sector(1500.0), null, EstadoTicket.PENDIENTE_PAGO);
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(List.of(pendiente));

        service.liberar(RESERVA_ID, EstadoPago.REINTEGRADO, "falla de emisión");

        verify(reservaVencidaService).liberar(List.of(pendiente), EstadoTicket.CANCELADO);
        assertThat(reserva.getEstadoPago()).isEqualTo(EstadoPago.REINTEGRADO);
        assertThat(reserva.getMotivoRechazo()).isEqualTo("falla de emisión");
    }

    @Test
    void liberar_reservaYaResuelta_noHaceNada() {
        Reserva reserva = reserva(EstadoPago.APROBADO);
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));

        service.liberar(RESERVA_ID, EstadoPago.REINTEGRADO, "falla de emisión");

        verifyNoInteractions(reservaVencidaService);
        assertThat(reserva.getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
    }

    private void prepararReserva(Reserva reserva, DisponibilidadEvento evento, List<Ticket> tickets) {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(eventoRepository.findByIdForUpdate(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(tickets);
    }

    private Reserva reserva(EstadoPago estado) {
        return Reserva.builder()
                .id(RESERVA_ID)
                .eventoId(EVENTO_ID)
                .clienteId(CLIENTE_ID)
                .sectorIdExterno(3L)
                .expiraEl(Instant.now().plusSeconds(600))
                .creadoEl(Instant.now())
                .estadoPago(estado)
                .idempotencyKey(estado == null ? null : CLAVE)
                .build();
    }

    private DisponibilidadEvento evento(Long organizadorId) {
        return DisponibilidadEvento.builder()
                .eventoId(EVENTO_ID)
                .organizadorId(organizadorId)
                .creadoEl(Instant.now())
                .build();
    }

    private DisponibilidadSector sector(Double precio) {
        return DisponibilidadSector.builder()
                .id(10L)
                .sectorIdExterno(3L)
                .tipo(TipoSector.NUMERADO)
                .capacidadTotal(10)
                .disponibles(8)
                .precio(precio)
                .build();
    }

    private Ticket ticket(Long id, DisponibilidadSector sector, DisponibilidadAsiento asiento, EstadoTicket estado) {
        return Ticket.builder()
                .id(id)
                .codigo_qr("qr-" + id)
                .estadoTicket(estado)
                .clienteId(CLIENTE_ID)
                .expiraEl(Instant.now().plusSeconds(600))
                .sector(sector)
                .asiento(asiento)
                .build();
    }
}
