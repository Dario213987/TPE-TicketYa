package org.tudai.ticketya.tickets.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PreferenciasRedisConfig {

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(
            @Qualifier("redisConnectionFactory") LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

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
