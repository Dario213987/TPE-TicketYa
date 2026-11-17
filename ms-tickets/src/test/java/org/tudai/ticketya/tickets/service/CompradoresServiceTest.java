package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompradoresServiceTest {

    private static final List<EstadoTicket> ESTADOS_COMPRADOR = List.of(
            EstadoTicket.EMITIDO,
            EstadoTicket.USADO,
            EstadoTicket.DEVUELTO);

    @Mock
    private TicketRepository ticketRepository;

    private CompradoresService service;

    @BeforeEach
    void setUp() {
        service = new CompradoresService(ticketRepository);
    }

    @Test
    void listarCompradores_consultaSoloLosEstadosDeCompra() {
        when(ticketRepository.findClienteIdsDistintosPorEvento(7L, ESTADOS_COMPRADOR))
                .thenReturn(List.of(10L, 20L));

        List<Long> compradores = service.listarCompradores(7L);

        assertThat(compradores).containsExactly(10L, 20L);
        verify(ticketRepository).findClienteIdsDistintosPorEvento(7L, ESTADOS_COMPRADOR);
    }

    @Test
    void listarCompradores_cuandoNoHayTickets_devuelveListaVacia() {
        when(ticketRepository.findClienteIdsDistintosPorEvento(99L, ESTADOS_COMPRADOR))
                .thenReturn(List.of());

        assertThat(service.listarCompradores(99L)).isEmpty();
    }
}
