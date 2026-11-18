package org.tudai.ticketya.transacciones.service.temp_mock_gateway;

/**
 * Pasarela de pagos.
 * <p>
 * Las implementaciones deben ser idempotentes por {@link Orden#idempotencyKey()}: si reciben dos
 * veces la misma clave, cobran o acreditan una sola vez y devuelven el mismo resultado. La clave
 * se mantiene en todos los reintentos de una misma transacción.
 */
public interface PaymentGateway {

    record Orden(Long transaccionId, String idempotencyKey, Long ticketId, Long clienteId, Double monto) {}

    record Resultado(boolean aprobado, String motivo) {}

    Resultado debitar(Orden orden);

    Resultado acreditar(Orden orden);
}
