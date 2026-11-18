package org.tudai.ticketya.eventos.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de prueba con caches de distinto TTL: cuenta cuántas veces se ejecuta de verdad
 * (cada ejecución equivale a una consulta a la base).
 */
public class ServicioCacheadoDePrueba {

    private final AtomicInteger consultas = new AtomicInteger();

    /** Por método y no por campo: el bean es un proxy de la cache. */
    public AtomicInteger consultas() {
        return consultas;
    }

    @Cacheable("prueba-corta")
    public String corta(Long id) {
        return "corta-" + id + "-" + consultas.incrementAndGet();
    }

    @Cacheable("prueba-larga")
    public String larga(Long id) {
        return "larga-" + id + "-" + consultas.incrementAndGet();
    }

    @Cacheable("prueba-sin-ttl-propio")
    public String sinTtlPropio(Long id) {
        return "default-" + id + "-" + consultas.incrementAndGet();
    }

    @TestConfiguration
    static class Config {
        @Bean
        ServicioCacheadoDePrueba servicioCacheadoDePrueba() {
            return new ServicioCacheadoDePrueba();
        }
    }
}
