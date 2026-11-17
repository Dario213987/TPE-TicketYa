package org.tudai.ticketya.notificaciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_ENVIAR;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_FALLIDAS;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.EXCHANGE_DLX;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.ROUTING_FALLIDAS;

/**
 * Configuracion de RabbitMQ para consumir mensajes de notificacion.
 * <p>
 * Declara la cola principal, el exchange y la cola de mensajes fallidos (DLQ), y
 * configura reintentos con backoff exponencial. Al agotar los intentos el recoverer
 * marca las filas pendientes como fallidas y el broker enruta el mensaje a la cola de fallidas.
 */
@Configuration
@EnableRabbit
@EnableConfigurationProperties(ReintentosNotificacionProperties.class)
public class RabbitConfig {

    /**
     * Declara el exchange directo que recibe los mensajes rechazados.
     *
     * @return exchange DLX
     */
    @Bean
    public DirectExchange enviarDlx() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    /**
     * Declara la cola durable donde terminan los mensajes que agotan los reintentos.
     *
     * @return cola DLQ
     */
    @Bean
    public Queue colaFallidas() {
        return QueueBuilder.durable(COLA_FALLIDAS).build();
    }

    /**
     * Vincula la cola de fallidos al exchange DLX con la routing key configurada.
     *
     * @param colaFallidas cola de fallidos
     * @param enviarDlx   exchange de fallidos
     * @return binding entre la cola y el exchange
     */
    @Bean
    public Binding bindingFallidas(Queue colaFallidas, DirectExchange enviarDlx) {
        return BindingBuilder.bind(colaFallidas).to(enviarDlx).with(ROUTING_FALLIDAS);
    }

    /**
     * Declara la cola principal de envio con la DLX asociada.
     *
     * @return cola de envio
     */
    @Bean
    public Queue colaEnviar() {
        return QueueBuilder.durable(COLA_ENVIAR)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_FALLIDAS)
                .build();
    }

    /**
     * Crea el conversor JSON usado para deserializar {@code NotificacionEntranteDTO}.
     *
     * @return conversor de mensajes
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * Configura los intentos y el backoff exponencial desde propiedades.
     *
     * @return interceptor de reintentos
     */
    @Bean
    public RetryOperationsInterceptor retryInterceptor(
            ReintentosNotificacionProperties propiedades,
            MessageRecoverer messageRecoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(propiedades.getMaxIntentos())
                .backOffOptions(
                        propiedades.getIntervaloInicialMs(),
                        propiedades.getMultiplicador(),
                        propiedades.getIntervaloMaximoMs())
                .recoverer(messageRecoverer)
                .build();
    }

    /**
     * Crea la factory de listeners con el conversor JSON, los reintentos y el rechazo
     * sin reencolado para que los fallos terminen en la cola de fallidas.
     *
     * @param connectionFactory  conexion con RabbitMQ
     * @param jsonMessageConverter conversor JSON
     * @param retryInterceptor   interceptor de reintentos
     * @return factory configurada para los listeners
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}