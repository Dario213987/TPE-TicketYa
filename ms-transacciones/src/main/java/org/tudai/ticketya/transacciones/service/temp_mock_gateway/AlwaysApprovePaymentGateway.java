package org.tudai.ticketya.transacciones.service.temp_mock_gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Habria que ver si con esto alcanza o hay que hacer un micro para la pasarela, probablemente se termine haciendo
@Slf4j
@Component
public class AlwaysApprovePaymentGateway implements PaymentGateway {

    @Override
    public Resultado debitar(Orden orden) {
        log.info("Débito aprobado: {}", orden);
        return new Resultado(true, null);
    }

    @Override
    public Resultado acreditar(Orden orden) {
        log.info("Crédito aprobado: {}", orden);
        return new Resultado(true, null);
    }
}
