package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.EntradaReintegroDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntradaReintegroServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private EntradaReintegroService service;

    @Test
    @DisplayName("Lista solo entradas vendidas con el organizador y el débito original")
    void listarVendidas_mapeaLaConsulta() {
        when(ticketRepository.findEntradasParaReintegro(eq(500L), eq(0L), any(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{101L, 10L, 1500.0, EstadoTicket.VENDIDO, 99L, 700L}));

        List<EntradaReintegroDTO> entradas = service.listarVendidas(500L, 0L, 100);

        assertThat(entradas).singleElement().satisfies(entrada -> {
            assertThat(entrada.getId()).isEqualTo(101L);
            assertThat(entrada.getClienteId()).isEqualTo(10L);
            assertThat(entrada.getPrecio()).isEqualTo(1500.0);
            assertThat(entrada.getEstado()).isEqualTo("VENDIDO");
            assertThat(entrada.getOrganizadorId()).isEqualTo(99L);
            assertThat(entrada.getTransaccionId()).isEqualTo(700L);
        });
    }

    @Test
    @DisplayName("Marcar como devuelto es idempotente")
    void marcarDevuelto_onlyCambiaSiNoEstaDevuelto() {
        Ticket vendido = Ticket.builder().id(101L).estadoTicket(EstadoTicket.VENDIDO).build();
        Ticket devuelto = Ticket.builder().id(102L).estadoTicket(EstadoTicket.DEVUELTO).build();
        when(ticketRepository.findById(101L)).thenReturn(java.util.Optional.of(vendido));
        when(ticketRepository.findById(102L)).thenReturn(java.util.Optional.of(devuelto));

        service.marcarDevuelto(101L);
        service.marcarDevuelto(102L);

        assertThat(vendido.getEstadoTicket()).isEqualTo(EstadoTicket.DEVUELTO);
        verify(ticketRepository).save(vendido);
        verify(ticketRepository, never()).save(devuelto);
    }
}
