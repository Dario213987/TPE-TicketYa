package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.config.QrProperties;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.ValidacionQrResponseDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QrValidationServiceTest {

    private QrFirmaService qrFirmaService;
    private TicketRepository ticketRepository;
    private QrValidationService service;

    @BeforeEach
    void setUp() {
        QrProperties properties = new QrProperties();
        properties.setSecreto("secreto-de-prueba-con-longitud-suficiente");
        qrFirmaService = new QrFirmaService(properties);
        ticketRepository = mock(TicketRepository.class);
        service = new QrValidationService(qrFirmaService, ticketRepository);
    }

    @Test
    void validar_conFirmaInvalida_rechazaSinConsultarLaBase() {
        String codigo = qrFirmaService.firmar("ticket-123");
        String manipulado = codigo.substring(0, codigo.length() - 1)
                + (codigo.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.validar(manipulado))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("firma");

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void validar_conFirmaValida_consultaLaBaseYDevuelveLosDatosDelTicket() {
        String codigo = qrFirmaService.firmar("ticket-123");
        Ticket ticket = ticket(codigo);
        when(ticketRepository.findByCodigoQr(codigo)).thenReturn(Optional.of(ticket));

        ValidacionQrResponseDTO respuesta = service.validar(codigo);

        assertThat(respuesta.isValido()).isTrue();
        assertThat(respuesta.getTicketId()).isEqualTo(1L);
        assertThat(respuesta.getEventoId()).isEqualTo(7L);
        assertThat(respuesta.getSectorId()).isEqualTo(3L);
        assertThat(respuesta.getClienteId()).isEqualTo(42L);
        assertThat(respuesta.getEstado()).isEqualTo(EstadoTicket.EMITIDO);
    }

    @Test
    void validar_conFirmaValidaPeroTicketInexistente_lanzaNotFound() {
        String codigo = qrFirmaService.firmar("ticket-123");
        when(ticketRepository.findByCodigoQr(codigo)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(codigo))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("entrada");
    }

    private Ticket ticket(String codigo) {
        DisponibilidadEvento evento = DisponibilidadEvento.builder().eventoId(7L).build();
        DisponibilidadSector sector = DisponibilidadSector.builder()
                .id(10L)
                .sectorIdExterno(3L)
                .build();
        return Ticket.builder()
                .id(1L)
                .codigo_qr(codigo)
                .estadoTicket(EstadoTicket.EMITIDO)
                .clienteId(42L)
                .evento(evento)
                .sector(sector)
                .build();
    }
}
