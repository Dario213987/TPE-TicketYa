package org.tudai.ticketya.notificaciones.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_ENVIAR;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_FALLIDAS;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.EXCHANGE_DLX;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.ROUTING_FALLIDAS;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void declaraColaDeFallidasYEnrutaLosRechazosDesdeEnviar() {
        Queue fallidas = config.colaFallidas();
        Queue enviar = config.colaEnviar();
        DirectExchange dlx = config.enviarDlx();
        Binding binding = config.bindingFallidas(fallidas, dlx);

        assertThat(fallidas.getName()).isEqualTo(COLA_FALLIDAS);
        assertThat(fallidas.isDurable()).isTrue();
        assertThat(binding.getDestination()).isEqualTo(COLA_FALLIDAS);
        assertThat(binding.getExchange()).isEqualTo(EXCHANGE_DLX);
        assertThat(binding.getRoutingKey()).isEqualTo(ROUTING_FALLIDAS);
        assertThat(enviar.getName()).isEqualTo(COLA_ENVIAR);
        assertThat(enviar.getArguments())
                .containsEntry("x-dead-letter-exchange", EXCHANGE_DLX)
                .containsEntry("x-dead-letter-routing-key", ROUTING_FALLIDAS);
    }
}
