package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el contrato real con Redis del script Lua de la fila.
 * <p>
 * Se omite automáticamente cuando no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
class FilaRedisRepositoryIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private FilaRedisRepository repository;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        repository = new FilaRedisRepository(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void registraEnOrdenYNoDuplicaAlCliente() {
        FilaRedisRepository.Registro primero = repository.registrar(7L, 42L);
        FilaRedisRepository.Registro segundo = repository.registrar(7L, 43L);
        FilaRedisRepository.Registro repetido = repository.registrar(7L, 42L);

        assertThat(primero.agregado()).isTrue();
        assertThat(primero.posicion()).isEqualTo(1);
        assertThat(segundo.agregado()).isTrue();
        assertThat(segundo.posicion()).isEqualTo(2);
        assertThat(repetido.agregado()).isFalse();
        assertThat(repetido.posicion()).isEqualTo(1);
        assertThat(repetido.totalEnFila()).isEqualTo(2);
        assertThat(repetido.ingresadoEl()).isEqualTo(primero.ingresadoEl());
        assertThat(redisTemplate.type(FilaRedisRepository.clave(7L)))
                .isEqualTo(DataType.ZSET);
    }

    @Test
    void consultaLaPosicionYLaCantidadDePersonasDelante() {
        assertThat(repository.consultar(7L, 42L)).isEmpty();

        repository.registrar(7L, 42L);
        repository.registrar(7L, 43L);
        FilaRedisRepository.Posicion posicion = repository.consultar(7L, 43L).orElseThrow();

        assertThat(posicion.posicion()).isEqualTo(2);
        assertThat(posicion.personasDelante()).isEqualTo(1);
        assertThat(posicion.totalEnFila()).isEqualTo(2);
    }

    @Test
    void registraEnParalelo_asignaPosicionesUnicasDelUnoAlTotal() throws Exception {
        int cantidad = 20;
        ExecutorService pool = Executors.newFixedThreadPool(cantidad);

        try {
            List<Callable<FilaRedisRepository.Registro>> tareas = IntStream
                    .rangeClosed(1, cantidad)
                    .<Callable<FilaRedisRepository.Registro>>mapToObj(
                            cliente -> () -> repository.registrar(99L, (long) cliente))
                    .toList();

            Set<Long> posiciones = new HashSet<>();
            for (Future<FilaRedisRepository.Registro> futuro : pool.invokeAll(tareas)) {
                posiciones.add(futuro.get().posicion());
            }

            assertThat(posiciones)
                    .containsExactlyInAnyOrderElementsOf(
                            LongStream.rangeClosed(1, cantidad).boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }
}
