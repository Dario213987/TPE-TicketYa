package org.tudai.ticketya.transacciones.service.temp_mock_gateway;

public interface PaymentGateway {

    record Orden(Long transaccionId, Long ticketId, Long clienteId, Double monto) {}

    record Resultado(boolean aprobado, String motivo) {}

    Resultado debitar(Orden orden);

    Resultado acreditar(Orden orden);
}