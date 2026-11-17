package org.tudai.ticketya.tickets.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PreferenciasRedisConfig {

    @Bean
    public LettuceConnectionFactory preferenciasRedisConnectionFactory(PreferenciasProperties properties) {
        return new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(properties.getRedisHost(), properties.getRedisPort()));
    }

    @Bean
    public StringRedisTemplate preferenciasRedisTemplate(
            @Qualifier("preferenciasRedisConnectionFactory") LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
