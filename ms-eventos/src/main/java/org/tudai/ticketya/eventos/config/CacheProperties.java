package org.tudai.ticketya.eventos.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache de ms-eventos en Redis con Redisson (TKY-81).
 * <p>
 * Cada cache tiene su TTL en {@code ticketya.cache.ttl.<nombre>}; las que no figuran usan
 * {@code ticketya.cache.ttl-por-defecto}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ticketya.cache")
public class CacheProperties {

    private String redisHost = "localhost";

    private int redisPort = 6382;

    /** Tiempo máximo para conectarse a Redis. */
    private Duration connectTimeout = Duration.ofSeconds(1);

    /** Tiempo máximo de cada operación contra Redis. */
    private Duration timeout = Duration.ofSeconds(1);

    /** TTL de las caches que no tienen uno propio. */
    private Duration ttlPorDefecto = Duration.ofMinutes(5);

    /** TTL por cache: {@code ticketya.cache.ttl.<nombre>=PT5M}. */
    private Map<String, Duration> ttl = new LinkedHashMap<>();
}
