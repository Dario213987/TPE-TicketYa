package org.tudai.ticketya.preferencias.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.tudai.ticketya.preferencias.messaging.HistorialEventoListener;
import org.tudai.ticketya.preferencias.messaging.HistorialProperties;

import java.util.List;

/**
 * Levanta el consumidor de los streams del historial (US-09.02).
 * <p>
 * Crea el grupo de consumidores con {@code MKSTREAM}, así el servicio puede
 * arrancar antes de que ms-tickets publique el primer evento. Si Redis no está
 * disponible, deja un warning y el resto del servicio sigue funcionando.
 * Se puede apagar con {@code preferencias.historial.consumer-enabled=false}.
 */
@Configuration
@EnableConfigurationProperties(HistorialProperties.class)
@RequiredArgsConstructor
@Slf4j
public class HistorialStreamConfig {

    private final HistorialProperties properties;
    private final HistorialEventoListener listener;
    private final StringRedisTemplate redisTemplate;

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "preferencias.historial", name = "consumer-enabled",
            havingValue = "true", matchIfMissing = true)
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> historialStreamContainer() {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(properties.getPollTimeout())
                        .errorHandler(error -> log.warn(
                                "Historial: error al leer los streams: {}", error.getMessage()))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        crearGrupo(properties.getStreamVentaPagada());
        crearGrupo(properties.getStreamTicketUtilizado());

        Consumer consumer = Consumer.from(properties.getGrupo(), properties.getConsumidor());
        for (String stream : List.of(properties.getStreamVentaPagada(), properties.getStreamTicketUtilizado())) {
            container.receiveAutoAck(consumer,
                    StreamOffset.create(stream, ReadOffset.lastConsumed()),
                    listener);
        }
        container.start();

        log.info("Historial: consumiendo {} y {} con el grupo {} (consumidor {})",
                properties.getStreamVentaPagada(), properties.getStreamTicketUtilizado(),
                properties.getGrupo(), properties.getConsumidor());

        return container;
    }

    private void crearGrupo(String stream) {
        try {
            redisTemplate.opsForStream().createGroup(stream, ReadOffset.from("0"), properties.getGrupo());
        } catch (RedisSystemException e) {
            if (esGrupoExistente(e)) {
                log.debug("Historial: el grupo {} ya existe en {}", properties.getGrupo(), stream);
            } else {
                log.warn("Historial: no se pudo crear el grupo {} en {}: {}",
                        properties.getGrupo(), stream, e.getMessage());
            }
        } catch (DataAccessException e) {
            log.warn("Historial: Redis no disponible al crear el grupo {} en {}: {}. "
                            + "El consumidor arrancará cuando Redis esté disponible y se reinicie el servicio.",
                    properties.getGrupo(), stream, e.getMessage());
        }
    }

    private static boolean esGrupoExistente(RedisSystemException e) {
        String mensaje = e.getMessage() == null ? "" : e.getMessage();
        Throwable causa = e.getCause();
        return mensaje.contains("BUSYGROUP")
                || (causa != null && causa.getMessage() != null && causa.getMessage().contains("BUSYGROUP"));
    }
}
