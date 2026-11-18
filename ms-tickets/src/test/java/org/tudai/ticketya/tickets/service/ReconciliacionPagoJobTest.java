package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.tickets.config.ReconciliacionProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliacionPagoJobTest {

    @Mock
    private PagoService pagoService;

    @Mock
    private ReservaPagoService reservaPagoService;

    @Mock
    private BloqueoDistribuidoService bloqueoDistribuidoService;

    private final ReconciliacionProperties properties = new ReconciliacionProperties();
    private final BloqueoDistribuidoService.Bloqueo bloqueo =
            new BloqueoDistribuidoService.Bloqueo(ReconciliacionPagoJob.BLOQUEO, "yo");
    private ReconciliacionPagoJob job;

    @BeforeEach
    void setUp() {
        properties.setMargen(Duration.ofMinutes(1));
        job = new ReconciliacionPagoJob(pagoService, reservaPagoService, bloqueoDistribuidoService, properties);
    }

    @Test
    void otraInstanciaTieneElBloqueo_noHaceNada() {
        when(bloqueoDistribuidoService.adquirir(eq(ReconciliacionPagoJob.BLOQUEO), any())).thenReturn(Optional.empty());

        job.reconciliar();

        verifyNoInteractions(reservaPagoService, pagoService);
    }

    @Test
    void reconciliaLasReservasVencidasHaceMasDelMargenYLiberaElBloqueo() {
        when(bloqueoDistribuidoService.adquirir(eq(ReconciliacionPagoJob.BLOQUEO), any())).thenReturn(Optional.of(bloqueo));
        when(reservaPagoService.pendientesVencidas(any())).thenReturn(List.of(1L, 2L));
        when(pagoService.reconciliar(1L)).thenReturn(PagoService.Reconciliacion.EMITIDA);
        when(pagoService.reconciliar(2L)).thenReturn(PagoService.Reconciliacion.SIGUE_PENDIENTE);

        Instant antes = Instant.now();
        job.reconciliar();

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(reservaPagoService).pendientesVencidas(limite.capture());
        assertThat(limite.getValue()).isBeforeOrEqualTo(antes.minus(Duration.ofSeconds(59)));
        verify(pagoService).reconciliar(1L);
        verify(pagoService).reconciliar(2L);
        verify(bloqueoDistribuidoService).liberar(bloqueo);
    }

    @Test
    void siUnaReservaFalla_sigueConLasDemas() {
        when(bloqueoDistribuidoService.adquirir(eq(ReconciliacionPagoJob.BLOQUEO), any())).thenReturn(Optional.of(bloqueo));
        when(reservaPagoService.pendientesVencidas(any())).thenReturn(List.of(1L, 2L));
        when(pagoService.reconciliar(1L)).thenThrow(new RemoteServiceException(503, "caído"));
        when(pagoService.reconciliar(2L)).thenReturn(PagoService.Reconciliacion.EMITIDA);

        job.reconciliar();

        verify(pagoService).reconciliar(2L);
        verify(bloqueoDistribuidoService).liberar(bloqueo);
    }
}
