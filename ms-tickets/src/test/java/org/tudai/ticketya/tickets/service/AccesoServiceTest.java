package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.exception.AccesoRechazadoException;
import org.tudai.ticketya.tickets.exception.CodigoRechazoAcceso;
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

    @Mock
    private QrFirmaService qrFirmaService;

    private AccesoService service;

    @BeforeEach
    void setUp() {
        service = new AccesoService(ticketRepository, qrFirmaService);
    }

    @Test
    void validar_ticketVendidoDelEvento_loMarcaComoUsado() {
        qrValido();
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
    void validar_qrInexistente_lanzaRechazoNoExiste() {
        qrValido();
        when(ticketRepository.marcarComoUsado(QR, EVENTO_ID, EstadoTicket.VENDIDO, EstadoTicket.USADO))
                .thenReturn(0);
        when(ticketRepository.buscarPorCodigoQr(QR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(AccesoRechazadoException.class)
                .hasMessageContaining("No existe")
                .satisfies(ex -> assertThat(((AccesoRechazadoException) ex).getCodigo())
                        .isEqualTo(CodigoRechazoAcceso.NO_EXISTE));
    }

    @Test
    void validar_ticketDeOtroEvento_lanzaRechazoOtroEvento() {
        qrValido();
        prepararRechazo(ticket(9L, EstadoTicket.VENDIDO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(AccesoRechazadoException.class)
                .hasMessageContaining("evento")
                .satisfies(ex -> assertThat(((AccesoRechazadoException) ex).getCodigo())
                        .isEqualTo(CodigoRechazoAcceso.OTRO_EVENTO));
    }

    @Test
    void validar_ticketYaUsado_lanzaRechazoYaUsada() {
        qrValido();
        prepararRechazo(ticket(EVENTO_ID, EstadoTicket.USADO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(AccesoRechazadoException.class)
                .hasMessageContaining("ya fue utilizado")
                .satisfies(ex -> assertThat(((AccesoRechazadoException) ex).getCodigo())
                        .isEqualTo(CodigoRechazoAcceso.YA_USADA));
    }

    @Test
    void validar_ticketDevuelto_lanzaRechazoDevuelta() {
        qrValido();
        prepararRechazo(ticket(EVENTO_ID, EstadoTicket.DEVUELTO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(AccesoRechazadoException.class)
                .hasMessageContaining("devuelto")
                .satisfies(ex -> assertThat(((AccesoRechazadoException) ex).getCodigo())
                        .isEqualTo(CodigoRechazoAcceso.DEVUELTA));
    }

    @Test
    void validar_ticketNoVendido_lanzaConflictExceptionSinCodigo() {
        qrValido();
        prepararRechazo(ticket(EVENTO_ID, EstadoTicket.EMITIDO));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(ConflictException.class)
                .isNotInstanceOf(AccesoRechazadoException.class)
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

    @Test
    void validar_firmaInvalida_lanzaBadRequestSinConsultarElRepositorio() {
        when(qrFirmaService.verificar(QR))
                .thenThrow(new BadRequestException("La firma del QR es inválida"));

        assertThatThrownBy(() -> service.validar(EVENTO_ID, QR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("firma");

        verifyNoInteractions(ticketRepository);
    }

    private void qrValido() {
        when(qrFirmaService.verificar(QR)).thenReturn(QR);
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
