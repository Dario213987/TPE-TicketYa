package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.MiEntradaDTO;
import org.tudai.ticketya.tickets.dto.QrEntradaDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntradaServiceTest {

    private static final Long CLIENTE_ID = 42L;

    @Mock
    private TicketRepository ticketRepository;

    private EntradaService service;

    @BeforeEach
    void setUp() {
        service = new EntradaService(ticketRepository);
    }

    @Test
    void listar_devuelveLasEntradasDelClienteConEventoSectorAsientoYEstado() {
        Ticket numerado = ticket(1L, EstadoTicket.VENDIDO, asiento(2, 5));
        Ticket usado = ticket(2L, EstadoTicket.USADO, null);
        when(ticketRepository.findByClienteIdAndEstadoTicketIn(eq(CLIENTE_ID), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(numerado, usado)));

        Page<MiEntradaDTO> entradas = service.listar(CLIENTE_ID, PageRequest.of(0, 20));

        assertThat(entradas.getContent())
                .extracting(MiEntradaDTO::getId, MiEntradaDTO::getEventoId, MiEntradaDTO::getSectorId,
                        MiEntradaDTO::getFila, MiEntradaDTO::getNumero, MiEntradaDTO::getEstado,
                        MiEntradaDTO::isUtilizado, MiEntradaDTO::getPrecio)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 7L, 3L, 2, 5, EstadoTicket.VENDIDO, false, 1500.0),
                        org.assertj.core.groups.Tuple.tuple(2L, 7L, 3L, null, null, EstadoTicket.USADO, true, 1500.0));
    }

    @Test
    void listar_soloPideLosEstadosDeEntradaOrdenadosDeLaMasNueva() {
        when(ticketRepository.findByClienteIdAndEstadoTicketIn(eq(CLIENTE_ID), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(CLIENTE_ID, PageRequest.of(1, 5, Sort.by("precio")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<EstadoTicket>> estados = ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findByClienteIdAndEstadoTicketIn(eq(CLIENTE_ID), estados.capture(), pagina.capture());
        assertThat(estados.getValue())
                .containsExactlyInAnyOrder(EstadoTicket.EMITIDO, EstadoTicket.VENDIDO, EstadoTicket.USADO,
                        EstadoTicket.DEVUELTO)
                .doesNotContain(EstadoTicket.RESERVADO, EstadoTicket.PENDIENTE_PAGO, EstadoTicket.CANCELADO,
                        EstadoTicket.EXPIRADO);
        assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
        assertThat(pagina.getValue().getSort().getOrderFor("fechaEmision").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pagina.getValue().getSort().getOrderFor("precio")).isNull();
    }

    @Test
    void qr_entradaVigenteDelCliente_devuelveElCodigoUnico() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L, EstadoTicket.VENDIDO, null)));

        QrEntradaDTO qr = service.qr(1L, CLIENTE_ID);

        assertThat(qr.getTicketId()).isEqualTo(1L);
        assertThat(qr.getContenido()).isEqualTo("qr-1");
    }

    @Test
    void qr_entradaDeOtroCliente_lanzaNotFound() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L, EstadoTicket.VENDIDO, null)));

        assertThatThrownBy(() -> service.qr(1L, 1000L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void qr_ticketInexistente_lanzaNotFound() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.qr(1L, CLIENTE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void qr_entradaQueNoEstaVigente_lanzaConflict() {
        for (EstadoTicket estado : List.of(EstadoTicket.USADO, EstadoTicket.DEVUELTO, EstadoTicket.RESERVADO,
                EstadoTicket.PENDIENTE_PAGO, EstadoTicket.CANCELADO)) {
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L, estado, null)));

            assertThatThrownBy(() -> service.qr(1L, CLIENTE_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(estado.name());
        }
    }

    private Ticket ticket(Long id, EstadoTicket estado, DisponibilidadAsiento asiento) {
        return Ticket.builder()
                .id(id)
                .codigo_qr("qr-" + id)
                .estadoTicket(estado)
                .clienteId(CLIENTE_ID)
                .precio(1500.0)
                .fechaEmision(Instant.now())
                .evento(DisponibilidadEvento.builder().eventoId(7L).build())
                .sector(DisponibilidadSector.builder().id(10L).sectorIdExterno(3L).build())
                .asiento(asiento)
                .build();
    }

    private DisponibilidadAsiento asiento(int fila, int numero) {
        return DisponibilidadAsiento.builder().fila(fila).numero(numero).estado(EstadoDisponibilidad.VENDIDO).build();
    }
}
