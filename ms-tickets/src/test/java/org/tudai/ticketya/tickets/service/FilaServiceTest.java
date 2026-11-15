package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoVenta;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.FilaRedisRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilaServiceTest {

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private FilaRedisRepository filaRepository;

    private FilaService service;

    @BeforeEach
    void setUp() {
        service = new FilaService(eventoRepository, filaRepository);
    }

    @Test
    void entrar_eventoDeAltaDemanda_agregaAlClienteYDevuelveSuPosicion() {
        when(eventoRepository.findById(7L))
                .thenReturn(Optional.of(evento(true, EstadoVenta.ABIERTA)));
        when(filaRepository.registrar(7L, 42L))
                .thenReturn(new FilaRedisRepository.Registro(
                        true, 1, 1, Instant.ofEpochMilli(1_000)));

        TurnoFilaResponseDTO turno = service.entrar(7L, 42L);

        assertThat(turno.getEventoId()).isEqualTo(7L);
        assertThat(turno.getClienteId()).isEqualTo(42L);
        assertThat(turno.getPosicion()).isEqualTo(1L);
        assertThat(turno.getTotalEnFila()).isEqualTo(1L);
        assertThat(turno.isYaEstabaEnFila()).isFalse();
        assertThat(turno.getIngresadoEl()).isEqualTo(Instant.ofEpochMilli(1_000));

        verify(filaRepository).registrar(7L, 42L);
    }

    @Test
    void entrar_siElClienteYaEstaba_noDuplicaNiCambiaSuPosicion() {
        when(eventoRepository.findById(7L))
                .thenReturn(Optional.of(evento(true, EstadoVenta.ABIERTA)));
        when(filaRepository.registrar(7L, 42L))
                .thenReturn(new FilaRedisRepository.Registro(
                        false, 3, 5, Instant.ofEpochMilli(500)));

        TurnoFilaResponseDTO turno = service.entrar(7L, 42L);

        assertThat(turno.getPosicion()).isEqualTo(3L);
        assertThat(turno.getTotalEnFila()).isEqualTo(5L);
        assertThat(turno.isYaEstabaEnFila()).isTrue();
        assertThat(turno.getIngresadoEl()).isEqualTo(Instant.ofEpochMilli(500));
    }

    @Test
    void entrar_eventoSinAltaDemanda_lanzaConflictYNoTocaRedis() {
        when(eventoRepository.findById(7L))
                .thenReturn(Optional.of(evento(false, EstadoVenta.ABIERTA)));

        assertThatThrownBy(() -> service.entrar(7L, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no es de alta demanda");

        verifyNoInteractions(filaRepository);
    }

    @Test
    void entrar_eventoConVentaCerrada_lanzaConflictYNoTocaRedis() {
        when(eventoRepository.findById(7L))
                .thenReturn(Optional.of(evento(true, EstadoVenta.SUSPENDIDA)));

        assertThatThrownBy(() -> service.entrar(7L, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está abierta")
                .hasMessageContaining("SUSPENDIDA");

        verifyNoInteractions(filaRepository);
    }

    @Test
    void entrar_eventoAgotado_lanzaConflictYNoTocaRedis() {
        when(eventoRepository.findById(7L))
                .thenReturn(Optional.of(evento(true, EstadoVenta.AGOTADA)));

        assertThatThrownBy(() -> service.entrar(7L, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está abierta")
                .hasMessageContaining("AGOTADA");

        verifyNoInteractions(filaRepository);
    }

    @Test
    void entrar_eventoSinDisponibilidad_lanzaNotFoundYNoTocaRedis() {
        when(eventoRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar(7L, 42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No hay disponibilidad para el evento 7");

        verifyNoInteractions(filaRepository);
    }

    private DisponibilidadEvento evento(boolean altaDemanda, EstadoVenta estadoVenta) {
        return DisponibilidadEvento.builder()
                .eventoId(7L)
                .creadoEl(Instant.now())
                .altaDemanda(altaDemanda)
                .estadoVenta(estadoVenta)
                .build();
    }
}
