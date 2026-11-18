package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.tickets.config.ReservaProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaVencidaJobTest {

    private static final String BLOQUEO = "liberar-reservas-vencidas";

    @Mock
    private ReservaVencidaService reservaVencidaService;

    @Mock
    private BloqueoDistribuidoService bloqueoDistribuidoService;

    @Mock
    private ReservaProperties properties;

    private ReservaVencidaJob job;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBloqueoLiberacion()).thenReturn(Duration.ofMinutes(5));
        job = new ReservaVencidaJob(reservaVencidaService, bloqueoDistribuidoService, properties);
    }

    @Test
    void cuandoAdquiereElBloqueo_liberaLasReservasYLoSuelta() {
        BloqueoDistribuidoService.Bloqueo bloqueo =
                new BloqueoDistribuidoService.Bloqueo(BLOQUEO, "duenio");
        when(bloqueoDistribuidoService.adquirir(eq(BLOQUEO), any(Duration.class)))
                .thenReturn(Optional.of(bloqueo));
        when(reservaVencidaService.liberarVencidas(any(Instant.class))).thenReturn(2);

        job.liberarReservasVencidas();

        verify(reservaVencidaService).liberarVencidas(any(Instant.class));
        verify(bloqueoDistribuidoService).liberar(bloqueo);
    }

    @Test
    void cuandoOtraInstanciaTieneElBloqueo_noLiberaNiSueltaNada() {
        when(bloqueoDistribuidoService.adquirir(anyString(), any(Duration.class)))
                .thenReturn(Optional.empty());

        job.liberarReservasVencidas();

        verifyNoInteractions(reservaVencidaService);
        verify(bloqueoDistribuidoService, never()).liberar(any());
    }

    @Test
    void cuandoFallaLaLiberacion_sueltaElBloqueoYPropagaElError() {
        BloqueoDistribuidoService.Bloqueo bloqueo =
                new BloqueoDistribuidoService.Bloqueo(BLOQUEO, "duenio");
        when(bloqueoDistribuidoService.adquirir(eq(BLOQUEO), any(Duration.class)))
                .thenReturn(Optional.of(bloqueo));
        when(reservaVencidaService.liberarVencidas(any(Instant.class)))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> job.liberarReservasVencidas())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(bloqueoDistribuidoService).liberar(bloqueo);
    }
}
