package org.tudai.ticketya.eventos.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache con Redis real (Testcontainers): cada cache usa su TTL configurado por propiedades
 * (TKY-81).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventos-cache;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "eureka.client.enabled=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs",
        "ticketya.cache.ttl.prueba-corta=PT1S",
        "ticketya.cache.ttl.prueba-larga=PT1M",
        "ticketya.cache.ttl-por-defecto=PT2S"
})
@Import(ServicioCacheadoDePrueba.Config.class)
@Testcontainers(disabledWithoutDocker = true)
class CacheRedisTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("ticketya.cache.redis-host", REDIS::getHost);
        registry.add("ticketya.cache.redis-port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    ServicioCacheadoDePrueba servicio;

    @Autowired
    RedissonClient redissonCacheClient;

    @BeforeEach
    void limpiar() {
        redissonCacheClient.getKeys().flushall();
        servicio.consultas().set(0);
    }

    @Test
    void laSegundaLlamadaSaleDeLaCache() {
        String primera = servicio.larga(1L);

        assertThat(servicio.larga(1L)).isEqualTo(primera);
        assertThat(servicio.consultas().get()).isEqualTo(1);
    }

    @Test
    void cadaCacheVenceConSuPropioTtl() throws InterruptedException {
        String corta = servicio.corta(1L);
        String larga = servicio.larga(1L);

        Thread.sleep(1_500);

        assertThat(servicio.corta(1L)).isNotEqualTo(corta);
        assertThat(servicio.larga(1L)).isEqualTo(larga);
        assertThat(servicio.consultas().get()).isEqualTo(3);
    }

    @Test
    void elTtlQuedaGuardadoEnRedisSegunLaPropiedadDeCadaCache() {
        servicio.corta(1L);
        servicio.larga(1L);
        servicio.sinTtlPropio(1L);

        assertThat(redissonCacheClient.getMapCache("prueba-corta").remainTimeToLive(1L))
                .isPositive().isLessThanOrEqualTo(1_000);
        assertThat(redissonCacheClient.getMapCache("prueba-larga").remainTimeToLive(1L))
                .isGreaterThan(50_000).isLessThanOrEqualTo(60_000);
        // Sin TTL propio usa ticketya.cache.ttl-por-defecto (PT2S en este test): nunca queda sin vencimiento.
        assertThat(redissonCacheClient.getMapCache("prueba-sin-ttl-propio").remainTimeToLive(1L))
                .isGreaterThan(1_000).isLessThanOrEqualTo(2_000);
    }
}
