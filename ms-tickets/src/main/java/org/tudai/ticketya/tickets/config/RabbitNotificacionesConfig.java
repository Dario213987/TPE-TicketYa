package org.tudai.ticketya.tickets.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_ENVIAR;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.EXCHANGE_DLX;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.ROUTING_DLQ;

/**
 * Configuración RabbitMQ productora de notificaciones. Declara la cola "enviar" con los mismos
 * argumentos DLX que el consumidor (ms-notificaciones) para compatibilidad idempotente.
 */
@Configuration
public class RabbitNotificacionesConfig {

    @Bean
    public Queue colaEnviarNotificaciones() {
        return QueueBuilder.durable(COLA_ENVIAR)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_DLQ)
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverterNotificaciones() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplateNotificaciones(ConnectionFactory connectionFactory,
                                                       MessageConverter jsonMessageConverterNotificaciones) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverterNotificaciones);
        return template;
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
