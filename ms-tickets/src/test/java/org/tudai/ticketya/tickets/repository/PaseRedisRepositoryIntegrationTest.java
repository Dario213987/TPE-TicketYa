package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica contra Redis real que las tandas extraen de la cola de forma atomica
 * y que los pases vencen solos. Se omite automaticamente sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class PaseRedisRepositoryIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private FilaRedisRepository filaRepository;
    private PaseRedisRepository paseRepository;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        filaRepository = new FilaRedisRepository(redisTemplate);
        paseRepository = new PaseRedisRepository(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void habilitaLosPrimerosNClientesYElSiguienteTandaTomaLosRestantes() {
        for (long cliente = 1; cliente <= 5; cliente++) {
            filaRepository.registrar(7L, cliente);
        }

        List<PaseRedisRepository.Pase> primera = paseRepository.habilitarSiguientes(
                7L, 2, Duration.ofMinutes(5), "tanda-1");
        List<PaseRedisRepository.Pase> segunda = paseRepository.habilitarSiguientes(
                7L, 2, Duration.ofMinutes(5), "tanda-2");

        assertThat(primera).extracting(PaseRedisRepository.Pase::clienteId)
                .containsExactly(1L, 2L);
        assertThat(segunda).extracting(PaseRedisRepository.Pase::clienteId)
                .containsExactly(3L, 4L);
        assertThat(filaRepository.totalEnFila(7L)).isEqualTo(1);
        assertThat(paseRepository.buscar(7L, 1L)).isPresent();
        assertThat(paseRepository.buscar(7L, 5L)).isEmpty();
        assertThat(redisTemplate.getExpire(
                PaseRedisRepository.clave(7L, 1L), java.util.concurrent.TimeUnit.SECONDS))
                .isBetween(1L, 300L);
    }

    @Test
    void clienteConPaseNoVuelveAEntrarALaFila() {
        filaRepository.registrar(7L, 42L);
        paseRepository.habilitarSiguientes(7L, 1, Duration.ofMinutes(5), "tanda-1");

        FilaRedisRepository.Registro registro = filaRepository.registrar(7L, 42L);

        assertThat(registro.yaHabilitado()).isTrue();
        assertThat(registro.agregado()).isFalse();
        assertThat(filaRepository.totalEnFila(7L)).isZero();
    }

    @Test
    void tandasConcurrentesNoDuplicanNiSalteanClientes() throws Exception {
        int clientes = 40;
        int tamanoTanda = 5;
        int tandas = clientes / tamanoTanda;
        for (long cliente = 1; cliente <= clientes; cliente++) {
            filaRepository.registrar(7L, cliente);
        }

        ExecutorService pool = Executors.newFixedThreadPool(tandas);
        try {
            List<Callable<List<PaseRedisRepository.Pase>>> tareas = IntStream
                    .rangeClosed(1, tandas)
                    .<Callable<List<PaseRedisRepository.Pase>>>mapToObj(
                            tanda -> () -> paseRepository.habilitarSiguientes(
                                    7L, tamanoTanda, Duration.ofMinutes(5), "tanda-" + tanda))
                    .toList();

            List<Long> habilitados = new ArrayList<>();
            for (Future<List<PaseRedisRepository.Pase>> futuro : pool.invokeAll(tareas)) {
                futuro.get().forEach(pase -> habilitados.add(pase.clienteId()));
            }

            Set<Long> unicos = new HashSet<>(habilitados);
            assertThat(habilitados).hasSize(clientes);
            assertThat(unicos).hasSize(clientes);
            assertThat(unicos)
                    .containsExactlyInAnyOrderElementsOf(
                            IntStream.rangeClosed(1, clientes).mapToObj(Long::valueOf).toList());
            assertThat(filaRepository.totalEnFila(7L)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void elPaseVenceSolo() throws Exception {
        filaRepository.registrar(7L, 42L);
        paseRepository.habilitarSiguientes(7L, 1, Duration.ofMillis(150), "tanda-1");

        assertThat(paseRepository.existe(7L, 42L)).isTrue();

        long limite = System.currentTimeMillis() + 3_000;
        while (paseRepository.existe(7L, 42L) && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }

        assertThat(paseRepository.existe(7L, 42L)).isFalse();
        assertThat(paseRepository.buscar(7L, 42L)).isEmpty();
    }
}
