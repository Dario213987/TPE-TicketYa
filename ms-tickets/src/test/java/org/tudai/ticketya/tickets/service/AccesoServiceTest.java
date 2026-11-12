package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccesoServiceTest {

    private static final Long EVENTO_ID = 5L;
    private static final String QR = "qr-1";

    @Mock
    private TicketRepository ticketRepository;

    private AccesoService service;

    @BeforeEach
    void setUp() {
        service = new AccesoService(ticketRepository);
    }

    @Test
    void validar_ticketVendidoDelEvento_loMarcaComoUsado() {
        when(ticketRepository.marcarComoUsado(QR, EVENTO_ID, EstadoTicket.VENDIDO, EstadoTicket.USADO))
                .thenReturn(1);

        ResultadoAccesoDTO resultado = service.validar(EVENTO_ID, "  " + QR + "  ");

        assertThat(resultado.getResultado()).isEqualTo("OK");
        assertThat(resultado.isValido()).isTrue();
        assertThat(resultado.getCodigoQr()).isEqualTo(QR);
        assertThat(resultado.getEventoId()).isEqualTo(EVENTO_ID);
        assertThat(resultado.getEstadoTicket()).isEqualTo(EstadoTicket.USADO);
        verify(ticketRepository, never()).buscarPorCodigoQr(anyString());
    }

    @Test
    void validar_qrInexistente_lanzaNotFoundException() {
        when(ticketRepository.marcarComoUsado(QR, EVENTO_ID, EstadoTicket.VENDIDO, EstadoTicket.USADO))
                .thenReturn(0);
        when(ticketRepository.buscarPorCodigoQr(QR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No existe");
    }

    @Test
    void validar_ticketDeOtroEvento_lanzaConflictException() {
        prepararRechazo(ticket(9L, EstadoTicket.VENDIDO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("evento");
    }

    @Test
    void validar_ticketYaUsado_lanzaConflictException() {
        prepararRechazo(ticket(EVENTO_ID, EstadoTicket.USADO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue utilizado");
    }

    @Test
    void validar_ticketDevuelto_lanzaConflictException() {
        prepararRechazo(ticket(EVENTO_ID, EstadoTicket.DEVUELTO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("devuelto");
    }

    @Test
    void validar_ticketNoVendido_lanzaConflictException() {
        prepararRechazo(ticket(EVENTO_ID, EstadoTicket.EMITIDO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no esta habilitado");
    }

    @Test
    void validar_qrVacio_lanzaBadRequestSinConsultarElRepositorio() {
        assertThatThrownBy(() -> service.validar(EVENTO_ID, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("codigo QR");

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void validar_eventoNulo_lanzaBadRequestSinConsultarElRepositorio() {
        assertThatThrownBy(() -> service.validar(null, QR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("evento");

        verifyNoInteractions(ticketRepository);
    }

    private void prepararRechazo(Ticket ticket) {
        when(ticketRepository.marcarComoUsado(QR, EVENTO_ID, EstadoTicket.VENDIDO, EstadoTicket.USADO))
                .thenReturn(0);
        when(ticketRepository.buscarPorCodigoQr(QR)).thenReturn(Optional.of(ticket));
    }

    private Ticket ticket(Long eventoId, EstadoTicket estado) {
        return Ticket.builder()
                .codigo_qr(QR)
                .estadoTicket(estado)
                .evento(DisponibilidadEvento.builder().eventoId(eventoId).build())
                .build();
    }
}
