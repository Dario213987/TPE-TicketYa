package org.tudai.ticketya.eventos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis caído (nadie escucha en el puerto): la aplicación arranca y las llamadas cacheadas
 * responden consultando la base, sin errores y sin demorarse (TKY-81).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventos-cache-caido;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
        "ticketya.cache.ttl-por-defecto=PT2S",
        "ticketya.cache.redis-host=127.0.0.1",
        "ticketya.cache.redis-port=" + CacheRedisCaidoTest.PUERTO_SIN_REDIS
})
@Import(ServicioCacheadoDePrueba.Config.class)
class CacheRedisCaidoTest {

    /** Puerto reservado por el sistema y cerrado: la conexión se rechaza al instante. */
    static final int PUERTO_SIN_REDIS = 1;

    @Autowired
    ServicioCacheadoDePrueba servicio;

    @Test
    void conRedisCaido_laLlamadaConsultaLaBaseSinFallar() {
        long inicio = System.nanoTime();

        String primera = servicio.larga(1L);
        String segunda = servicio.larga(1L);

        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
        assertThat(primera).isEqualTo("larga-1-1");
        assertThat(segunda).isEqualTo("larga-1-2");
        assertThat(servicio.consultas().get()).isEqualTo(2);
        assertThat(duracionMs).isLessThan(5_000);
    }

    @Autowired
    org.springframework.cache.CacheManager cacheManager;

    @Test
    void conRedisCaido_laAplicacionArrancaYObtenerUnaCacheNoFalla() {
        // Si el contexto no hubiera arrancado, este test ni se ejecutaría: al iniciar, las
        // métricas de Actuator crean las caches configuradas y Redisson le manda un comando a Redis.
        assertThat(cacheManager.getCache("prueba-larga")).isNotNull();
        assertThat(cacheManager.getCache("otra-cache-nueva")).isNotNull();
    }

    @Test
    void elPuertoDePruebaEstaLibre() throws Exception {
        // Si algo escuchara en el puerto, el test anterior no probaría un Redis caído.
        boolean ocupado;
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", PUERTO_SIN_REDIS), 300);
            ocupado = true;
        } catch (java.io.IOException e) {
            ocupado = false;
        }
        assertThat(ocupado).isFalse();
    }
}
