package org.tudai.ticketya.transacciones.service;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.transacciones.service.temp_mock_gateway.PaymentGateway;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Llama a la pasarela de pagos con timeout y reintentos con backoff exponencial.
 * <p>
 * Cada intento corta a los {@code ticketya.pasarela.timeout-ms}. Los fallos técnicos (timeout o
 * excepción) se reintentan hasta {@code ticketya.pasarela.max-intentos} veces, esperando
 * {@code ticketya.pasarela.backoff-inicial-ms} y duplicando la espera en cada intento. Un rechazo
 * de la pasarela no es un fallo técnico: vuelve como {@link PaymentGateway.Resultado} y no se
 * reintenta.
 * <p>
 * Reintentar es seguro porque todos los intentos mandan la misma {@code Orden}, con la misma
 * clave de idempotencia.
 */
@Slf4j
@Component
public class PasarelaResiliente {

    static final int STATUS_TIMEOUT = 504;
    static final int STATUS_NO_DISPONIBLE = 503;

    private final PaymentGateway gateway;
    private final long timeoutMs;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PasarelaResiliente(PaymentGateway gateway,
                              @Value("${ticketya.pasarela.timeout-ms:3000}") long timeoutMs,
                              @Value("${ticketya.pasarela.max-intentos:3}") int maxIntentos,
                              @Value("${ticketya.pasarela.backoff-inicial-ms:200}") long backoffInicialMs) {
        this.gateway = gateway;
        this.timeoutMs = timeoutMs;
        this.timeLimiter = TimeLimiter.of("pasarela", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .cancelRunningFuture(true)
                .build());
        this.retry = Retry.of("pasarela", RetryConfig.custom()
                .maxAttempts(maxIntentos)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(backoffInicialMs), 2.0))
                .retryExceptions(Exception.class)
                .build());
        this.retry.getEventPublisher().onRetry(evento -> log.warn(
                "Reintento {} de la pasarela en {} ms: {}", evento.getNumberOfRetryAttempts(),
                evento.getWaitInterval().toMillis(), String.valueOf(evento.getLastThrowable())));
    }

    /**
     * Debita o acredita la orden en la pasarela.
     *
     * @throws RemoteServiceException si la pasarela no respondió a tiempo (504) o siguió fallando
     *                                después de todos los intentos (503)
     */
    public PaymentGateway.Resultado procesar(TipoTransaccion tipo, PaymentGateway.Orden orden) {
        Callable<PaymentGateway.Resultado> intento = () -> timeLimiter.executeFutureSupplier(
                () -> executor.submit(conMdc(() -> llamar(tipo, orden))));
        try {
            return Retry.decorateCallable(retry, intento).call();
        } catch (TimeoutException e) {
            throw new RemoteServiceException(STATUS_TIMEOUT,
                    "La pasarela de pagos no respondió en " + timeoutMs + " ms (orden " + orden.idempotencyKey() + ")");
        } catch (Exception e) {
            throw new RemoteServiceException(STATUS_NO_DISPONIBLE,
                    "La pasarela de pagos no está disponible (orden " + orden.idempotencyKey() + "): " + e.getMessage());
        }
    }

    private PaymentGateway.Resultado llamar(TipoTransaccion tipo, PaymentGateway.Orden orden) {
        return tipo == TipoTransaccion.DEBITO ? gateway.debitar(orden) : gateway.acreditar(orden);
    }

    /** Copia el MDC (correlationId, etc.) al hilo que llama a la pasarela. */
    private static <T> Callable<T> conMdc(Callable<T> tarea) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return tarea.call();
            } finally {
                MDC.clear();
            }
        };
    }

    @PreDestroy
    void cerrar() {
        executor.shutdownNow();
    }
}
