package org.tudai.ticketya.transacciones.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>
 * Un circuit breaker envuelve los reintentos (TKY-86): una llamada que agota sus intentos cuenta
 * como una sola falla, y un rechazo de la pasarela no cuenta. Si fallan al menos
 * {@code ticketya.pasarela.circuito.porcentaje-fallas}% de las últimas
 * {@code ticketya.pasarela.circuito.ventana} llamadas (con un mínimo de
 * {@code ticketya.pasarela.circuito.minimo-llamadas}), el circuito se abre durante
 * {@code ticketya.pasarela.circuito.espera-abierto-ms} y las llamadas fallan al instante con
 * {@link PasarelaNoDisponibleException}, sin llegar a la pasarela. Después deja pasar
 * {@code ticketya.pasarela.circuito.llamadas-semiabierto} llamadas de prueba para decidir si se cierra.
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
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** Para tests: circuito con los valores por defecto. */
    PasarelaResiliente(PaymentGateway gateway, long timeoutMs, int maxIntentos, long backoffInicialMs) {
        this(gateway, timeoutMs, maxIntentos, backoffInicialMs, 20, 5, 50, 30_000, 3);
    }

    @Autowired
    public PasarelaResiliente(PaymentGateway gateway,
                              @Value("${ticketya.pasarela.timeout-ms:2000}") long timeoutMs,
                              @Value("${ticketya.pasarela.max-intentos:3}") int maxIntentos,
                              @Value("${ticketya.pasarela.backoff-inicial-ms:200}") long backoffInicialMs,
                              @Value("${ticketya.pasarela.circuito.ventana:10}") int ventana,
                              @Value("${ticketya.pasarela.circuito.minimo-llamadas:5}") int minimoLlamadas,
                              @Value("${ticketya.pasarela.circuito.porcentaje-fallas:50}") float porcentajeFallas,
                              @Value("${ticketya.pasarela.circuito.espera-abierto-ms:30000}") long esperaAbiertoMs,
                              @Value("${ticketya.pasarela.circuito.llamadas-semiabierto:3}") int llamadasSemiabierto) {
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
        this.circuitBreaker = CircuitBreaker.of("pasarela", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(ventana)
                .minimumNumberOfCalls(minimoLlamadas)
                .failureRateThreshold(porcentajeFallas)
                .waitDurationInOpenState(Duration.ofMillis(esperaAbiertoMs))
                .permittedNumberOfCallsInHalfOpenState(llamadasSemiabierto)
                .build());
        this.circuitBreaker.getEventPublisher().onStateTransition(evento -> log.warn(
                "Circuito de la pasarela: {}", evento.getStateTransition()));
    }

    /**
     * Debita o acredita la orden en la pasarela.
     *
     * @throws PasarelaNoDisponibleException si el circuito está abierto: no se llamó a la pasarela
     * @throws RemoteServiceException          si la pasarela no respondió a tiempo (504) o siguió
     *                                         fallando después de todos los intentos (503)
     */
    public PaymentGateway.Resultado procesar(TipoTransaccion tipo, PaymentGateway.Orden orden) {
        Callable<PaymentGateway.Resultado> intento = () -> timeLimiter.executeFutureSupplier(
                () -> executor.submit(conMdc(() -> llamar(tipo, orden))));
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, Retry.decorateCallable(retry, intento)).call();
        } catch (CallNotPermittedException e) {
            throw new PasarelaNoDisponibleException(orden.idempotencyKey());
        } catch (TimeoutException e) {
            throw new RemoteServiceException(STATUS_TIMEOUT,
                    "La pasarela de pagos no respondió en " + timeoutMs + " ms (orden " + orden.idempotencyKey() + ")");
        } catch (Exception e) {
            throw new RemoteServiceException(STATUS_NO_DISPONIBLE,
                    "La pasarela de pagos no está disponible (orden " + orden.idempotencyKey() + "): " + e.getMessage());
        }
    }

    /** El circuito de la pasarela, para consultar su estado (y en tests, abrirlo o reiniciarlo). */
    public CircuitBreaker circuito() {
        return circuitBreaker;
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
