package org.tudai.ticketya.tickets.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PreferenciasRedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PreferenciasRedisConfig.class, ConsumidorConfig.class)
            .withBean(PreferenciasProperties.class)
            .withPropertyValues(
                    "spring.data.redis.host=redis-app",
                    "spring.data.redis.port=6380",
                    "tickets.preferencias.redis-host=redis-preferencias",
                    "tickets.preferencias.redis-port=6381");

    @Test
    void inyeccionSinCalificadorResuelveElTemplatePrincipalDeLaAplicacion() {
        contextRunner.run(context -> {
            assertThat(context.getBean("consumidorSinCalificador"))
                    .isSameAs(context.getBean("stringRedisTemplate"));
            assertThat(context.getBean("preferenciasRedisTemplate"))
                    .isNotSameAs(context.getBean("stringRedisTemplate"));
        });
    }

    @Configuration
    static class ConsumidorConfig {

        @Bean
        StringRedisTemplate consumidorSinCalificador(StringRedisTemplate template) {
            return template;
        }
    }
}
