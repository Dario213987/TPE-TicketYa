package org.tudai.ticketya.transacciones.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.transacciones.service.temp_mock_gateway.PaymentGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasarelaResilienteTest {

    private static final PaymentGateway.Orden ORDEN =
            new PaymentGateway.Orden(5L, "tx-5", 10L, 7L, 100.0);
    private static final PaymentGateway.Resultado APROBADO = new PaymentGateway.Resultado(true, null);

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private PasarelaResiliente pasarela;

    @AfterEach
    void cerrar() {
        if (pasarela != null) {
            pasarela.cerrar();
        }
    }

    @Test
    void pasarelaLenta_cortaPorTimeout() {
        pasarela = new PasarelaResiliente(gateway, 100, 1, 10);
        when(gateway.debitar(any())).thenAnswer(invocacion -> {
            Thread.sleep(2_000);
            return APROBADO;
        });

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> pasarela.procesar(TipoTransaccion.DEBITO, ORDEN))
                .isInstanceOfSatisfying(RemoteServiceException.class, e ->
                        assertThat(e.getRemoteStatus()).isEqualTo(PasarelaResiliente.STATUS_TIMEOUT))
                .hasMessageContaining("100 ms");
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(duracionMs).isLessThan(1_000);
    }

    @Test
    void falloTransitorio_reintentaConBackoffYAprueba() {
        pasarela = new PasarelaResiliente(gateway, 1_000, 3, 50);
        when(gateway.debitar(any()))
                .thenThrow(new IllegalStateException("conexión rechazada"))
                .thenThrow(new IllegalStateException("conexión rechazada"))
                .thenReturn(APROBADO);

        long inicio = System.nanoTime();
        PaymentGateway.Resultado resultado = pasarela.procesar(TipoTransaccion.DEBITO, ORDEN);
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(resultado.aprobado()).isTrue();
        verify(gateway, times(3)).debitar(any());
        // Backoff exponencial: 50 ms y después 100 ms.
        assertThat(duracionMs).isGreaterThanOrEqualTo(150);
    }

    @Test
    void reintentos_usanSiempreLaMismaClaveDeIdempotencia() {
        pasarela = new PasarelaResiliente(gateway, 1_000, 3, 10);
        when(gateway.debitar(any()))
                .thenThrow(new IllegalStateException("caída"))
                .thenReturn(APROBADO);

        pasarela.procesar(TipoTransaccion.DEBITO, ORDEN);

        ArgumentCaptor<PaymentGateway.Orden> ordenes = ArgumentCaptor.forClass(PaymentGateway.Orden.class);
        verify(gateway, times(2)).debitar(ordenes.capture());
        assertThat(ordenes.getAllValues()).extracting(PaymentGateway.Orden::idempotencyKey)
                .containsExactly("tx-5", "tx-5");
    }

    @Test
    void timeout_tambienSeReintenta() {
        pasarela = new PasarelaResiliente(gateway, 100, 2, 10);
        when(gateway.acreditar(any()))
                .thenAnswer(invocacion -> {
                    Thread.sleep(2_000);
                    return APROBADO;
                })
                .thenReturn(APROBADO);

        PaymentGateway.Resultado resultado = pasarela.procesar(TipoTransaccion.CREDITO, ORDEN);

        assertThat(resultado.aprobado()).isTrue();
        verify(gateway, times(2)).acreditar(any());
    }

    @Test
    void rechazoDeLaPasarela_noSeReintenta() {
        pasarela = new PasarelaResiliente(gateway, 1_000, 3, 10);
        when(gateway.debitar(any())).thenReturn(new PaymentGateway.Resultado(false, "Fondos insuficientes"));

        PaymentGateway.Resultado resultado = pasarela.procesar(TipoTransaccion.DEBITO, ORDEN);

        assertThat(resultado.aprobado()).isFalse();
        verify(gateway, times(1)).debitar(any());
    }

    @Test
    void agotaLosIntentos_lanza503() {
        pasarela = new PasarelaResiliente(gateway, 1_000, 3, 10);
        when(gateway.debitar(any())).thenThrow(new IllegalStateException("caída"));

        assertThatThrownBy(() -> pasarela.procesar(TipoTransaccion.DEBITO, ORDEN))
                .isInstanceOfSatisfying(RemoteServiceException.class, e ->
                        assertThat(e.getRemoteStatus()).isEqualTo(PasarelaResiliente.STATUS_NO_DISPONIBLE))
                .hasMessageContaining("caída");
        verify(gateway, times(3)).debitar(any());
    }
}
