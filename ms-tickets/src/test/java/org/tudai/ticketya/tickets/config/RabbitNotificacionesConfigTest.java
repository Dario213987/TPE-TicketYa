package org.tudai.ticketya.tickets.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_ENVIAR;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.EXCHANGE_DLX;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.ROUTING_FALLIDAS;

class RabbitNotificacionesConfigTest {

    private final RabbitNotificacionesConfig config = new RabbitNotificacionesConfig();

    @Test
    void declaraEnviarConRoutingHaciaColaDeFallidas() {
        Queue enviar = config.colaEnviarNotificaciones();

        assertThat(enviar.getName()).isEqualTo(COLA_ENVIAR);
        assertThat(enviar.getArguments())
                .containsEntry("x-dead-letter-exchange", EXCHANGE_DLX)
                .containsEntry("x-dead-letter-routing-key", ROUTING_FALLIDAS);
    }
}
