package org.tudai.ticketya.tickets.client;

import feign.RetryableException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.core.exception.RemoteServiceException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Reintenta con backoff exponencial las llamadas a ms-transacciones (TKY-85).
 * <p>
 * Solo se reintentan los fallos técnicos: timeout o error de conexión ({@link RetryableException})
 * y respuestas 5xx ({@link RemoteServiceException} con status &gt;= 500). Un 4xx no se reintenta.
 * <p>
 * Reintentar es seguro porque débitos y créditos son idempotentes en ms-transacciones: repetir el
 * pedido devuelve la misma transacción, y si quedó PENDIENTE se reprocesa con la misma clave de
 * idempotencia ({@code tx-<id>}) en la pasarela.
 */
@Slf4j
@Component
public class ReintentoTransacciones {

    private final Retry retry;

    public ReintentoTransacciones(
            @Value("${tickets.transacciones.max-intentos:2}") int maxIntentos,
            @Value("${tickets.transacciones.backoff-inicial-ms:300}") long backoffInicialMs) {
        this.retry = Retry.of("transacciones", RetryConfig.custom()
                .maxAttempts(maxIntentos)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(backoffInicialMs), 2.0))
                .retryOnException(ReintentoTransacciones::esFalloTecnico)
                .build());
        this.retry.getEventPublisher().onRetry(evento -> log.warn(
                "Reintento {} de ms-transacciones en {} ms: {}", evento.getNumberOfRetryAttempts(),
                evento.getWaitInterval().toMillis(), String.valueOf(evento.getLastThrowable())));
    }

    public <T> T ejecutar(Supplier<T> llamada) {
        return Retry.decorateSupplier(retry, llamada).get();
    }

    static boolean esFalloTecnico(Throwable e) {
        return e instanceof RetryableException
                || (e instanceof RemoteServiceException remota && remota.getRemoteStatus() >= 500);
    }
}
