package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaseRedisRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private PaseRedisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PaseRedisRepository(redisTemplate);
    }

    @Test
    void habilitarSiguientes_interpretaClientesTokenScoreYFechas() {
        doReturn(List.of(
                "42", "tanda-1:42", "1000000", "1700000000000", "1700000300000",
                "43", "tanda-1:43", "1000001", "1700000000000", "1700000300000"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());

        List<PaseRedisRepository.Pase> pases = repository.habilitarSiguientes(
                7L, 2, Duration.ofMinutes(5), "tanda-1");

        assertThat(pases).hasSize(2);
        assertThat(pases.get(0).clienteId()).isEqualTo(42L);
        assertThat(pases.get(0).token()).isEqualTo("tanda-1:42");
        assertThat(pases.get(0).tanda()).isEqualTo("tanda-1");
        assertThat(pases.get(0).score()).isEqualTo(1_000_000L);
        assertThat(pases.get(0).emitidoEl()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(pases.get(0).expiraEl()).isEqualTo(Instant.ofEpochMilli(1_700_000_300_000L));

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of(FilaRedisRepository.clave(7L),
                        PaseRedisRepository.claveActivos(7L))),
                eq(PaseRedisRepository.prefijo(7L)),
                eq("2"), eq("300000"), eq("tanda-1"));
    }

    @Test
    void reponerVencidos_interpretaLosPasesDeReposicion() {
        doReturn(List.of(
                "43", "tanda-2:43", "2000000", "1700000000000", "1700000120000"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        List<PaseRedisRepository.Pase> pases = repository.reponerVencidos(
                7L, Duration.ofMinutes(2), "tanda-2");

        assertThat(pases).hasSize(1);
        assertThat(pases.get(0).clienteId()).isEqualTo(43L);
        assertThat(pases.get(0).token()).isEqualTo("tanda-2:43");
        assertThat(pases.get(0).tanda()).isEqualTo("tanda-2");
        assertThat(pases.get(0).expiraEl())
                .isEqualTo(Instant.ofEpochMilli(1_700_000_120_000L));

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of(PaseRedisRepository.claveActivos(7L),
                        FilaRedisRepository.clave(7L))),
                eq(PaseRedisRepository.prefijo(7L)),
                eq("120000"), eq("tanda-2"));
    }

    @Test
    void reponerVencidos_sinVencidosNiCola_devuelveListaVacia() {
        doReturn(List.of())
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        assertThat(repository.reponerVencidos(
                7L, Duration.ofMinutes(1), "tanda-2")).isEmpty();
    }

    @Test
    void reponerVencidos_validaTtlYTanda() {
        assertThatThrownBy(() -> repository.reponerVencidos(
                7L, Duration.ZERO, "tanda-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");

        assertThatThrownBy(() -> repository.reponerVencidos(
                7L, Duration.ofMinutes(1), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tanda");
    }

    @Test
    void habilitarSiguientes_siLaColaEstaVacia_devuelveListaVacia() {
        doReturn(List.of())
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());

        assertThat(repository.habilitarSiguientes(7L, 5, Duration.ofMinutes(1), "tanda-1"))
                .isEmpty();
    }

    @Test
    void habilitarSiguientes_conResultadoInvalido_lanzaIllegalState() {
        doReturn(List.of("42", "token"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());

        assertThatThrownBy(() -> repository.habilitarSiguientes(
                7L, 1, Duration.ofMinutes(1), "tanda-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evento 7");
    }

    @Test
    void habilitarSiguientes_validaCantidadTtlYTanda() {
        assertThatThrownBy(() -> repository.habilitarSiguientes(
                7L, 0, Duration.ofMinutes(1), "tanda-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cantidad");

        assertThatThrownBy(() -> repository.habilitarSiguientes(
                7L, 1, Duration.ZERO, "tanda-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");

        assertThatThrownBy(() -> repository.habilitarSiguientes(
                7L, 1, Duration.ofMinutes(1), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tanda");
    }

    @Test
    void buscar_siExiste_devuelveElPase() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(PaseRedisRepository.clave(7L, 42L)))
                .thenReturn(Map.of(
                        "clienteId", "42",
                        "token", "tanda-1:42",
                        "tanda", "tanda-1",
                        "score", "1000000",
                        "emitidoEl", "1700000000000",
                        "expiraEl", "1700000300000"));

        PaseRedisRepository.Pase pase = repository.buscar(7L, 42L).orElseThrow();

        assertThat(pase.token()).isEqualTo("tanda-1:42");
        assertThat(pase.tanda()).isEqualTo("tanda-1");
        assertThat(pase.expiraEl()).isEqualTo(Instant.ofEpochMilli(1_700_000_300_000L));
    }

    @Test
    void buscar_siNoExiste_devuelveVacio() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(PaseRedisRepository.clave(7L, 42L)))
                .thenReturn(Map.of());

        assertThat(repository.buscar(7L, 42L)).isEmpty();
    }

    @Test
    void buscar_siFaltaExpiraEl_usaElTtlRestante() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(PaseRedisRepository.clave(7L, 42L)))
                .thenReturn(Map.of("token", "tanda-1:42", "tanda", "tanda-1"));
        when(redisTemplate.getExpire(PaseRedisRepository.clave(7L, 42L), TimeUnit.MILLISECONDS))
                .thenReturn(60_000L);

        PaseRedisRepository.Pase pase = repository.buscar(7L, 42L).orElseThrow();

        assertThat(pase.expiraEl()).isAfter(Instant.now());
    }

    @Test
    void validar_aceptaElTokenCorrectoYRechazaOtro() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(PaseRedisRepository.clave(7L, 42L)))
                .thenReturn(Map.of("token", "tanda-1:42", "tanda", "tanda-1"));

        assertThat(repository.validar(7L, 42L, "tanda-1:42")).isTrue();
        assertThat(repository.validar(7L, 42L, null)).isTrue();
        assertThat(repository.validar(7L, 42L, "otro-token")).isFalse();
    }
}
