package org.tudai.ticketya.eventos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_ENVIAR;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_FALLIDAS;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.EXCHANGE_DLX;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.ROUTING_FALLIDAS;

/**
 * Declaracion RabbitMQ para que ms-eventos publique avisos de notificacion.
 * <p>
 * Usa las mismas constantes y argumentos que {@code ms-notificaciones} para que la
 * cola principal, su DLX y la DLQ sean compatibles entre productor y consumidor.
 */
@Configuration
public class RabbitNotificacionesConfig {

    /**
     * Declara el exchange directo de mensajes fallidos.
     *
     * @return exchange DLX
     */
    @Bean
    public DirectExchange notificacionesDlx() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    /**
     * Declara la cola durable de mensajes fallidos.
     *
     * @return cola DLQ
     */
    @Bean
    public Queue notificacionesFallidas() {
        return QueueBuilder.durable(COLA_FALLIDAS).build();
    }

    /**
     * Vincula la DLQ al exchange de fallidos.
     *
     * @param notificacionesFallidas cola de fallidos
     * @param notificacionesDlx exchange de fallidos
     * @return binding entre cola y exchange
     */
    @Bean
    public Binding notificacionesFallidasBinding(
            @Qualifier("notificacionesFallidas") Queue notificacionesFallidas,
            @Qualifier("notificacionesDlx") DirectExchange notificacionesDlx) {
        return BindingBuilder.bind(notificacionesFallidas).to(notificacionesDlx).with(ROUTING_FALLIDAS);
    }

    /**
     * Declara la cola principal con la DLX asociada.
     *
     * @return cola de envio
     */
    @Bean
    public Queue notificacionesEnviar() {
        return QueueBuilder.durable(COLA_ENVIAR)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_FALLIDAS)
                .build();
    }

    /**
     * Usa el mismo conversor JSON que el consumidor.
     *
     * @return conversor de mensajes
     */
    @Bean
    public MessageConverter notificacionesJsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public ConnectionFactoryCustomizer rabbitTimeoutCustomizer(
            @Value("${ticketya.rabbitmq.handshake-timeout-ms:2000}") int handshakeTimeoutMs,
            @Value("${ticketya.rabbitmq.shutdown-timeout-ms:2000}") int shutdownTimeoutMs) {
        return factory -> {
            factory.setHandshakeTimeout(handshakeTimeoutMs);
            factory.setShutdownTimeout(shutdownTimeoutMs);
        };
    }
}
