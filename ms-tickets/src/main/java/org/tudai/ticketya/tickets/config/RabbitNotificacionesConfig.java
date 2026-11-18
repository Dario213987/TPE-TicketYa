package org.tudai.ticketya.tickets.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración RabbitMQ productora de notificaciones. Declara la cola "enviar" con los mismos
 * argumentos DLX que el consumidor (ms-notificaciones) para compatibilidad idempotente.
 */
@Configuration
public class RabbitNotificacionesConfig {

    public static final String COLA_ENVIAR = "enviar";
    public static final String EXCHANGE_DLX = "enviar.dlx";
    public static final String ROUTING_DLQ = "enviar.dlq";

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
}
