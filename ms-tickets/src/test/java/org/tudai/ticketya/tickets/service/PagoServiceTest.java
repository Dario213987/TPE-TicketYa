package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    private static final Long RESERVA_ID = 99L;
    private static final Long EVENTO_ID = 7L;
    private static final Long CLIENTE_ID = 42L;
    private static final Long ORGANIZADOR_ID = 5L;
    private static final String CLAVE = "clave-1";

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ReservaVencidaService reservaVencidaService;

    @Mock
    private TransaccionesApiClient transaccionesClient;

    private PagoService service;

    @BeforeEach
    void setUp() {
        service = new PagoService(reservaRepository, eventoRepository, ticketRepository,
                reservaVencidaService, transaccionesClient);
    }

    @Test
    void pagar_reservaVigenteYPagoAprobado_emiteLosTicketsVendidos() {
        Reserva reserva = reserva();
        DisponibilidadSector sector = sector(1500.0);
        DisponibilidadAsiento asiento = asiento(sector, 2, 5);
        Ticket numerado = ticket(1L, sector, asiento);
        Ticket otro = ticket(2L, sector, null);
        prepararReservaVigente(reserva, evento(ORGANIZADOR_ID), List.of(numerado, otro));
        when(transaccionesClient.generarDebito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));

        CompraResponseDTO compra = service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA"));

        ArgumentCaptor<GenerarTransaccionRequest> debitos = ArgumentCaptor.forClass(GenerarTransaccionRequest.class);
        verify(transaccionesClient, times(2)).generarDebito(debitos.capture());
        assertThat(debitos.getAllValues())
                .extracting(GenerarTransaccionRequest::getTicketId, GenerarTransaccionRequest::getClienteId,
                        GenerarTransaccionRequest::getMonto)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, CLIENTE_ID, 1500.0),
                        org.assertj.core.groups.Tuple.tuple(2L, CLIENTE_ID, 1500.0));

        assertThat(List.of(numerado, otro)).allSatisfy(ticket -> {
            assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.VENDIDO);
            assertThat(ticket.getPrecio()).isEqualTo(1500.0);
            assertThat(ticket.getExpiraEl()).isNull();
        });
        assertThat(asiento.getEstado()).isEqualTo(EstadoDisponibilidad.VENDIDO);

        assertThat(reserva.getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(reserva.getIdempotencyKey()).isEqualTo(CLAVE);
        assertThat(reserva.getMedioPago()).isEqualTo("TARJETA");
        assertThat(reserva.getPagadoEl()).isNotNull();

        assertThat(compra.getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(compra.getMedioPago()).isEqualTo("TARJETA");
        assertThat(compra.getTotal()).isEqualTo(3000.0);
        assertThat(compra.getTickets()).hasSize(2);
        assertThat(compra.getTickets().get(0).getCodigoQr()).isEqualTo("qr-1");
        assertThat(compra.getTickets().get(0).getFila()).isEqualTo(2);
        assertThat(compra.getTickets().get(0).getNumero()).isEqualTo(5);
        verify(reservaVencidaService).liberarVencidas(eq(EVENTO_ID), any(Instant.class));
    }

    @Test
    void pagar_reintentoConLaMismaClave_devuelveElResultadoOriginalSinCobrar() {
        Reserva reserva = reserva();
        reserva.setEstadoPago(EstadoPago.APROBADO);
        reserva.setIdempotencyKey(CLAVE);
        reserva.setMedioPago("TARJETA");
        DisponibilidadSector sector = sector(1500.0);
        Ticket vendido = ticket(1L, sector, null);
        vendido.setEstadoTicket(EstadoTicket.VENDIDO);
        vendido.setPrecio(1500.0);
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(List.of(vendido));

        CompraResponseDTO compra = service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA"));

        assertThat(compra.getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(compra.getTickets()).singleElement()
                .satisfies(ticket -> assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.VENDIDO));
        verifyNoInteractions(transaccionesClient, eventoRepository);
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void pagar_reservaYaProcesadaConOtraClave_lanzaConflictException() {
        Reserva reserva = reserva();
        reserva.setEstadoPago(EstadoPago.APROBADO);
        reserva.setIdempotencyKey("otra-clave");
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue procesada");

        verifyNoInteractions(transaccionesClient);
    }

    @Test
    void pagar_pagoRechazado_liberaLaReservaYLanzaPagoRechazado() {
        Reserva reserva = reserva();
        List<Ticket> tickets = List.of(ticket(1L, sector(1500.0), null));
        prepararReservaVigente(reserva, evento(ORGANIZADOR_ID), tickets);
        when(transaccionesClient.generarDebito(any()))
                .thenReturn(transaccion(EstadoTransaccion.FALLIDA).motivoFallo("fondos insuficientes"));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(PagoRechazadoException.class)
                .hasMessageContaining("fondos insuficientes");

        verify(reservaVencidaService).liberar(tickets, EstadoTicket.CANCELADO);
        assertThat(reserva.getEstadoPago()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(reserva.getMotivoRechazo()).isEqualTo("fondos insuficientes");
        assertThat(reserva.getIdempotencyKey()).isEqualTo(CLAVE);
        assertThat(reserva.getMedioPago()).isEqualTo("TARJETA");
        verify(reservaRepository).save(reserva);
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void pagar_reintentoDeUnPagoRechazado_devuelveElMismoRechazoSinCobrar() {
        Reserva reserva = reserva();
        reserva.setEstadoPago(EstadoPago.RECHAZADO);
        reserva.setIdempotencyKey(CLAVE);
        reserva.setMotivoRechazo("fondos insuficientes");
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(PagoRechazadoException.class)
                .hasMessageContaining("fondos insuficientes");

        verifyNoInteractions(transaccionesClient, reservaVencidaService);
    }

    @Test
    void pagar_elRechazoNoDeshaceLaLiberacionDeLaReserva() throws NoSuchMethodException {
        Transactional transactional = PagoService.class
                .getMethod("pagar", Long.class, Long.class, String.class, PagoRequestDTO.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.noRollbackFor()).contains(PagoRechazadoException.class);
    }

    @Test
    void pagar_reservaDeOtroCliente_lanzaForbiddenException() {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva()));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, 1000L, CLAVE, pago("TARJETA")))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(transaccionesClient);
    }

    @Test
    void pagar_reservaInexistente_lanzaNotFoundException() {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void pagar_reservaVencida_lanzaConflictExceptionSinCobrar() {
        Ticket expirado = ticket(1L, sector(1500.0), null);
        expirado.setEstadoTicket(EstadoTicket.EXPIRADO);
        prepararReservaVigente(reserva(), evento(ORGANIZADOR_ID), List.of(expirado));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("venció");

        verifyNoInteractions(transaccionesClient);
    }

    @Test
    void pagar_eventoSinOrganizador_lanzaConflictException() {
        prepararReservaVigente(reserva(), evento(null), List.of(ticket(1L, sector(1500.0), null)));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("organizador");

        verifyNoInteractions(transaccionesClient);
    }

    @Test
    void pagar_sectorSinPrecio_lanzaConflictException() {
        prepararReservaVigente(reserva(), evento(ORGANIZADOR_ID), List.of(ticket(1L, sector(null), null)));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago("TARJETA")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("precio");

        verifyNoInteractions(transaccionesClient);
    }

    @Test
    void pagar_sinClaveDeIdempotencia_lanzaBadRequestException() {
        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, " ", pago("TARJETA")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Idempotency-Key");

        verifyNoInteractions(reservaRepository, transaccionesClient);
    }

    private void prepararReservaVigente(Reserva reserva, DisponibilidadEvento evento, List<Ticket> tickets) {
        when(reservaRepository.findByIdForUpdate(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(eventoRepository.findByIdForUpdate(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(ticketRepository.findByReserva_IdOrderByIdAsc(RESERVA_ID)).thenReturn(tickets);
    }

    private Reserva reserva() {
        return Reserva.builder()
                .id(RESERVA_ID)
                .eventoId(EVENTO_ID)
                .clienteId(CLIENTE_ID)
                .sectorIdExterno(3L)
                .expiraEl(Instant.now().plusSeconds(600))
                .creadoEl(Instant.now())
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

    private DisponibilidadAsiento asiento(DisponibilidadSector sector, int fila, int numero) {
        return DisponibilidadAsiento.builder()
                .sector(sector)
                .fila(fila)
                .numero(numero)
                .estado(EstadoDisponibilidad.RESERVADO)
                .build();
    }

    private Ticket ticket(Long id, DisponibilidadSector sector, DisponibilidadAsiento asiento) {
        return Ticket.builder()
                .id(id)
                .codigo_qr("qr-" + id)
                .estadoTicket(EstadoTicket.RESERVADO)
                .clienteId(CLIENTE_ID)
                .expiraEl(Instant.now().plusSeconds(600))
                .sector(sector)
                .asiento(asiento)
                .build();
    }

    private TransaccionResponse transaccion(EstadoTransaccion estado) {
        return new TransaccionResponse().id(1L).estado(estado);
    }

    private PagoRequestDTO pago(String medioPago) {
        return PagoRequestDTO.builder().medioPago(medioPago).build();
    }
}
