package org.tudai.ticketya.tickets.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FilaRedisRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private FilaRedisRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FilaRedisRepository(redisTemplate);
    }

    @Test
    void registrar_clienteNuevo_interpretaPosicionTotalYMomentoDeIngreso() {
        doReturn(List.of(1L, 0L, 1L, 1_000_000L))
                .when(redisTemplate).execute(any(RedisScript.class), any(), any());

        FilaRedisRepository.Registro registro = repository.registrar(7L, 42L);

        assertThat(registro.agregado()).isTrue();
        assertThat(registro.posicion()).isEqualTo(1);
        assertThat(registro.totalEnFila()).isEqualTo(1);
        assertThat(registro.ingresadoEl()).isEqualTo(Instant.ofEpochSecond(1));

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of(FilaRedisRepository.clave(7L))), eq("42"));
    }

    @Test
    void registrar_clienteExistente_conservaAgregadoYPosicion() {
        doReturn(List.of(0L, 2L, 5L, 500_000L))
                .when(redisTemplate).execute(any(RedisScript.class), any(), any());

        FilaRedisRepository.Registro registro = repository.registrar(7L, 42L);

        assertThat(registro.agregado()).isFalse();
        assertThat(registro.posicion()).isEqualTo(3);
        assertThat(registro.totalEnFila()).isEqualTo(5);
        assertThat(registro.ingresadoEl())
                .isEqualTo(Instant.ofEpochSecond(0, 500_000_000));
    }

    @Test
    void registrar_siRedisNoDevuelveElResultadoEsperado_lanzaIllegalState() {
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), any(), any());

        assertThatThrownBy(() -> repository.registrar(7L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cliente 42")
                .hasMessageContaining("evento 7");
    }

    @Test
    void clave_usaElPrefijoYElEvento() {
        assertThat(FilaRedisRepository.clave(5L)).isEqualTo("tickets:fila:evento:5");
    }
}
