package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilaServiceTest {

    private static final String CLAVE = "tickets:fila:evento:7";
    private static final String MIEMBRO = "42";

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSet;

    private FilaService service;

    @BeforeEach
    void setUp() {
        service = new FilaService(eventoRepository, redisTemplate);
    }

    @Test
    void entrar_eventoDeAltaDemanda_agregaAlClienteYDevuelveSuPosicion() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(evento(true)));
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.addIfAbsent(eq(CLAVE), eq(MIEMBRO), anyDouble())).thenReturn(true);
        when(zSet.rank(CLAVE, MIEMBRO)).thenReturn(0L);
        when(zSet.zCard(CLAVE)).thenReturn(1L);
        when(zSet.score(CLAVE, MIEMBRO)).thenReturn(1_000.0);

        TurnoFilaResponseDTO turno = service.entrar(7L, 42L);

        assertThat(turno.getEventoId()).isEqualTo(7L);
        assertThat(turno.getClienteId()).isEqualTo(42L);
        assertThat(turno.getPosicion()).isEqualTo(1L);
        assertThat(turno.getTotalEnFila()).isEqualTo(1L);
        assertThat(turno.isYaEstabaEnFila()).isFalse();
        assertThat(turno.getIngresadoEl()).isEqualTo(Instant.ofEpochMilli(1_000));

        verify(zSet).addIfAbsent(eq(CLAVE), eq(MIEMBRO), anyDouble());
    }

    @Test
    void entrar_siElClienteYaEstaba_noDuplicaNiCambiaSuPosicion() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(evento(true)));
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.addIfAbsent(eq(CLAVE), eq(MIEMBRO), anyDouble())).thenReturn(false);
        when(zSet.rank(CLAVE, MIEMBRO)).thenReturn(2L);
        when(zSet.zCard(CLAVE)).thenReturn(5L);
        when(zSet.score(CLAVE, MIEMBRO)).thenReturn(500.0);

        TurnoFilaResponseDTO turno = service.entrar(7L, 42L);

        assertThat(turno.getPosicion()).isEqualTo(3L);
        assertThat(turno.getTotalEnFila()).isEqualTo(5L);
        assertThat(turno.isYaEstabaEnFila()).isTrue();
        assertThat(turno.getIngresadoEl()).isEqualTo(Instant.ofEpochMilli(500));

        verify(zSet).addIfAbsent(eq(CLAVE), eq(MIEMBRO), anyDouble());
    }

    @Test
    void entrar_eventoSinAltaDemanda_lanzaConflictYNoTocaRedis() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.of(evento(false)));

        assertThatThrownBy(() -> service.entrar(7L, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no es de alta demanda");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void entrar_eventoSinDisponibilidad_lanzaNotFoundYNoTocaRedis() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar(7L, 42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No hay disponibilidad para el evento 7");

        verifyNoInteractions(redisTemplate);
    }

    private DisponibilidadEvento evento(boolean altaDemanda) {
        return DisponibilidadEvento.builder()
                .eventoId(7L)
                .creadoEl(Instant.now())
                .altaDemanda(altaDemanda)
                .build();
    }
}
