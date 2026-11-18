package org.tudai.ticketya.eventos.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Capa de cache de ms-eventos: Redisson sobre Redis con TTL por cache (TKY-81).
 * <ul>
 *   <li><b>TTL por cache:</b> {@code ticketya.cache.ttl.<nombre>}; las caches sin TTL propio usan
 *   {@code ticketya.cache.ttl-por-defecto}, así ninguna queda sin vencimiento.</li>
 *   <li><b>Tolerante a fallos:</b> si Redis está caído, la aplicación arranca igual (el cliente se
 *   conecta recién al primer uso). Si no se puede crear una cache (Redisson le manda un comando a
 *   Redis al crearla), se usa una cache que no guarda nada para esa llamada y se reintenta en la
 *   siguiente. Los errores al leer, guardar o invalidar se registran como WARN y se ignoran. En
 *   todos los casos la llamada sigue contra la base. Los timeouts son cortos y sin reintentos para
 *   que un Redis caído no demore las respuestas.</li>
 * </ul>
 * Qué se cachea y cómo se invalida lo definen TKY-82 (detalle de evento) y TKY-83 (búsquedas).
 */
@Slf4j
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig implements CachingConfigurer {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonCacheClient(CacheProperties properties) {
        Config config = new Config();
        config.setLazyInitialization(true);
        config.useSingleServer()
                .setAddress("redis://" + properties.getRedisHost() + ":" + properties.getRedisPort())
                .setConnectTimeout((int) properties.getConnectTimeout().toMillis())
                .setTimeout((int) properties.getTimeout().toMillis())
                .setRetryAttempts(0);
        return Redisson.create(config);
    }

    @Bean
    public CacheManager redissonCacheManager(RedissonClient redissonCacheClient, CacheProperties properties) {
        Map<String, org.redisson.spring.cache.CacheConfig> configuraciones = new HashMap<>();
        properties.getTtl().forEach((nombre, ttl) ->
                configuraciones.put(nombre, new org.redisson.spring.cache.CacheConfig(ttl.toMillis(), 0)));
        long ttlPorDefecto = properties.getTtlPorDefecto().toMillis();

        return new RedissonSpringCacheManager(redissonCacheClient, configuraciones) {
            @Override
            protected org.redisson.spring.cache.CacheConfig createDefaultConfig() {
                return new org.redisson.spring.cache.CacheConfig(ttlPorDefecto, 0);
            }

            @Override
            public Cache getCache(String name) {
                try {
                    return super.getCache(name);
                } catch (RuntimeException e) {
                    // Redis caído al crear la cache (también al arrancar, por las métricas de Actuator).
                    log.warn("Cache {} no disponible; se consulta la base: {}", name, e.getMessage());
                    return new NoOpCache(name);
                }
            }
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheTolerante();
    }

    /** Ignora los errores de la cache: la llamada sigue contra la base. */
    static class CacheTolerante implements CacheErrorHandler {

        @Override
        public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
            log.warn("Cache {} no disponible al leer {}; se consulta la base: {}", cache.getName(), key, e.getMessage());
        }

        @Override
        public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
            log.warn("Cache {} no disponible al guardar {}: {}", cache.getName(), key, e.getMessage());
        }

        @Override
        public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
            log.warn("Cache {} no disponible al invalidar {}: {}", cache.getName(), key, e.getMessage());
        }

        @Override
        public void handleCacheClearError(RuntimeException e, Cache cache) {
            log.warn("Cache {} no disponible al vaciarla: {}", cache.getName(), e.getMessage());
        }
    }
}
