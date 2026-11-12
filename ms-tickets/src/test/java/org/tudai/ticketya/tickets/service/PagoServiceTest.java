package org.tudai.ticketya.tickets.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.RetryableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.tickets.domain.EstadoPago;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Cobro;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Preparacion;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    private static final Long RESERVA_ID = 99L;
    private static final Long CLIENTE_ID = 42L;
    private static final Long ORGANIZADOR_ID = 5L;
    private static final String CLAVE = "clave-1";

    @Mock
    private ReservaPagoService reservaPagoService;

    @Mock
    private TransaccionesApiClient transaccionesClient;

    private PagoService service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        service = new PagoService(reservaPagoService, transaccionesClient);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(PagoService.class)).addAppender(logs);
    }

    @AfterEach
    void quitarAppender() {
        ((Logger) LoggerFactory.getLogger(PagoService.class)).detachAppender(logs);
    }

    @Test
    void pagar_todosLosDebitosAprobados_emiteLasEntradas() {
        prepararCobros(new Cobro(1L, 1500.0), new Cobro(2L, 1500.0));
        when(transaccionesClient.generarDebito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));
        CompraResponseDTO emitida = compra(EstadoPago.APROBADO);
        when(reservaPagoService.emitir(RESERVA_ID)).thenReturn(emitida);

        assertThat(service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago())).isSameAs(emitida);

        ArgumentCaptor<GenerarTransaccionRequest> debitos = ArgumentCaptor.forClass(GenerarTransaccionRequest.class);
        verify(transaccionesClient, times(2)).generarDebito(debitos.capture());
        assertThat(debitos.getAllValues())
                .extracting(GenerarTransaccionRequest::getTicketId, GenerarTransaccionRequest::getClienteId,
                        GenerarTransaccionRequest::getMonto)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, CLIENTE_ID, 1500.0),
                        org.assertj.core.groups.Tuple.tuple(2L, CLIENTE_ID, 1500.0));
        verify(transaccionesClient, never()).generarCredito(any());
        verify(reservaPagoService, never()).liberar(any(), any(), any());
    }

    @Test
    void pagar_reintentoDeUnaCompraTerminada_devuelveLaOriginalSinCobrar() {
        CompraResponseDTO original = compra(EstadoPago.APROBADO);
        when(reservaPagoService.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .thenReturn(new Preparacion(original, null, List.of()));

        assertThat(service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago())).isSameAs(original);

        verifyNoInteractions(transaccionesClient);
        verify(reservaPagoService, never()).emitir(any());
    }

    @Test
    void pagar_rechazoParcial_reintegraLosAprobadosLiberaYLanzaPagoRechazado() {
        prepararCobros(new Cobro(1L, 1500.0), new Cobro(2L, 800.0));
        when(transaccionesClient.generarDebito(any()))
                .thenReturn(transaccion(EstadoTransaccion.COMPLETA))
                .thenReturn(transaccion(EstadoTransaccion.FALLIDA).motivoFallo("fondos insuficientes"));
        when(transaccionesClient.generarCredito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago()))
                .isInstanceOf(PagoRechazadoException.class)
                .hasMessageContaining("fondos insuficientes");

        ArgumentCaptor<GenerarTransaccionRequest> creditos = ArgumentCaptor.forClass(GenerarTransaccionRequest.class);
        verify(transaccionesClient).generarCredito(creditos.capture());
        assertThat(creditos.getValue().getTicketId()).isEqualTo(1L);
        assertThat(creditos.getValue().getMonto()).isEqualTo(1500.0);
        verify(reservaPagoService).liberar(RESERVA_ID, EstadoPago.RECHAZADO, "fondos insuficientes");
        verify(reservaPagoService, never()).emitir(any());
        assertThat(mensajes()).anyMatch(m -> m.contains("Compensación") && m.contains("ticket=1")
                && m.contains("reintegrado"));
    }

    @Test
    void pagar_primerDebitoRechazado_liberaSinReintegrar() {
        prepararCobros(new Cobro(1L, 1500.0));
        when(transaccionesClient.generarDebito(any())).thenReturn(transaccion(EstadoTransaccion.FALLIDA));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago()))
                .isInstanceOf(PagoRechazadoException.class);

        verify(transaccionesClient, never()).generarCredito(any());
        verify(reservaPagoService).liberar(eq(RESERVA_ID), eq(EstadoPago.RECHAZADO), anyString());
    }

    @Test
    void pagar_timeoutDeTransacciones_quedaPendienteSinLiberar() {
        prepararCobros(new Cobro(1L, 1500.0), new Cobro(2L, 1500.0));
        when(transaccionesClient.generarDebito(any()))
                .thenReturn(transaccion(EstadoTransaccion.COMPLETA))
                .thenThrow(mock(RetryableException.class));
        CompraResponseDTO pendiente = compra(EstadoPago.PENDIENTE_PAGO);
        when(reservaPagoService.consultar(RESERVA_ID)).thenReturn(pendiente);

        assertThat(service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago())).isSameAs(pendiente);

        verify(reservaPagoService, never()).liberar(any(), any(), any());
        verify(reservaPagoService, never()).emitir(any());
        verify(transaccionesClient, never()).generarCredito(any());
        assertThat(mensajes()).anyMatch(m -> m.contains("PENDIENTE_PAGO"));
    }

    @Test
    void pagar_errorDelServicioDeTransacciones_tambienQuedaPendiente() {
        prepararCobros(new Cobro(1L, 1500.0));
        when(transaccionesClient.generarDebito(any())).thenThrow(new RemoteServiceException(503, "caído"));
        when(reservaPagoService.consultar(RESERVA_ID)).thenReturn(compra(EstadoPago.PENDIENTE_PAGO));

        assertThat(service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago()).getEstadoPago())
                .isEqualTo(EstadoPago.PENDIENTE_PAGO);

        verify(reservaPagoService, never()).liberar(any(), any(), any());
    }

    @Test
    void pagar_reintentoDespuesDelTimeout_consultaElEstadoRealYEmite() {
        prepararCobros(new Cobro(1L, 1500.0));
        when(transaccionesClient.generarDebito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));
        when(reservaPagoService.emitir(RESERVA_ID)).thenReturn(compra(EstadoPago.APROBADO));

        assertThat(service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago()).getEstadoPago())
                .isEqualTo(EstadoPago.APROBADO);

        verify(transaccionesClient).generarDebito(any());
    }

    @Test
    void pagar_fallaLaEmision_reintegraTodoYLiberaLaReserva() {
        prepararCobros(new Cobro(1L, 1500.0), new Cobro(2L, 1500.0));
        when(transaccionesClient.generarDebito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));
        when(transaccionesClient.generarCredito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));
        when(reservaPagoService.emitir(RESERVA_ID)).thenThrow(new IllegalStateException("falla de base"));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("se reintegró el pago");

        verify(transaccionesClient, times(2)).generarCredito(any());
        verify(reservaPagoService).liberar(RESERVA_ID, EstadoPago.REINTEGRADO, PagoService.MOTIVO_FALLA_EMISION);
        assertThat(mensajes()).filteredOn(m -> m.contains("reintegrado")).hasSize(2);
        assertThat(mensajes()).anyMatch(m -> m.contains("Falló la emisión"));
    }

    @Test
    void pagar_fallaElReintegro_liberaIgualYQuedaReintegroPendiente() {
        prepararCobros(new Cobro(1L, 1500.0));
        when(transaccionesClient.generarDebito(any())).thenReturn(transaccion(EstadoTransaccion.COMPLETA));
        when(transaccionesClient.generarCredito(any())).thenThrow(new RemoteServiceException(503, "caído"));
        when(reservaPagoService.emitir(RESERVA_ID)).thenThrow(new IllegalStateException("falla de base"));

        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, CLAVE, pago()))
                .isInstanceOf(ConflictException.class);

        verify(reservaPagoService).liberar(RESERVA_ID, EstadoPago.REINTEGRO_PENDIENTE,
                PagoService.MOTIVO_FALLA_EMISION);
        assertThat(mensajes()).anyMatch(m -> m.contains("Compensación fallida") && m.contains("ticket=1"));
    }

    @Test
    void pagar_sinClaveDeIdempotencia_lanzaBadRequestException() {
        assertThatThrownBy(() -> service.pagar(RESERVA_ID, CLIENTE_ID, " ", pago()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Idempotency-Key");

        verifyNoInteractions(reservaPagoService, transaccionesClient);
    }

    private void prepararCobros(Cobro... cobros) {
        when(reservaPagoService.preparar(RESERVA_ID, CLIENTE_ID, CLAVE, "TARJETA"))
                .thenReturn(new Preparacion(null, ORGANIZADOR_ID, List.of(cobros)));
    }

    private List<String> mensajes() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private CompraResponseDTO compra(EstadoPago estado) {
        return CompraResponseDTO.builder().reservaId(RESERVA_ID).estadoPago(estado).build();
    }

    private TransaccionResponse transaccion(EstadoTransaccion estado) {
        return new TransaccionResponse().id(1L).estado(estado);
    }

    private PagoRequestDTO pago() {
        return PagoRequestDTO.builder().medioPago("TARJETA").build();
    }
}
