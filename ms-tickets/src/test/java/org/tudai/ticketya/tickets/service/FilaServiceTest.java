package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoFila;
import org.tudai.ticketya.tickets.domain.EstadoVenta;
import org.tudai.ticketya.tickets.dto.PosicionFilaResponseDTO;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.FilaRedisRepository;
import org.tudai.ticketya.tickets.repository.PaseRedisRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilaServiceTest {

    @Mock
    private DisponibilidadEventoRepository eventoRepository;

    @Mock
    private FilaRedisRepository filaRepository;

    @Mock
    private PaseRedisRepository paseRepository;

    private FilaService service;

    @BeforeEach
    void setUp() {
        service = new FilaService(eventoRepository, filaRepository, paseRepository);
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

    @Test
    void consultar_siElClienteEsta_devuelveSuPosicionSinTocarLaBase() {
        when(filaRepository.consultar(7L, 42L))
                .thenReturn(Optional.of(new FilaRedisRepository.Posicion(3, 2, 5)));

        PosicionFilaResponseDTO posicion = service.consultar(7L, 42L);

        assertThat(posicion.getEventoId()).isEqualTo(7L);
        assertThat(posicion.getClienteId()).isEqualTo(42L);
        assertThat(posicion.getPosicion()).isEqualTo(3L);
        assertThat(posicion.getPersonasDelante()).isEqualTo(2L);
        assertThat(posicion.getTotalEnFila()).isEqualTo(5L);

        verifyNoInteractions(eventoRepository);
    }

    @Test
    void consultar_siElClienteNoEsta_lanzaNotFoundSinTocarLaBase() {
        when(filaRepository.consultar(7L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(7L, 42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("El cliente 42 no está en la fila del evento 7");

        verifyNoInteractions(eventoRepository);
    }

    @Test
    void entrar_siElClienteYaTienePase_devuelveHabilitadoYNoTocaLaCola() {
        when(eventoRepository.findById(7L))
                .thenReturn(Optional.of(evento(true, EstadoVenta.ABIERTA)));
        when(paseRepository.buscar(7L, 42L))
                .thenReturn(Optional.of(pase()));
        when(filaRepository.totalEnFila(7L)).thenReturn(0L);

        TurnoFilaResponseDTO turno = service.entrar(7L, 42L);

        assertThat(turno.getEstado()).isEqualTo(EstadoFila.HABILITADO);
        assertThat(turno.getPase()).isNotNull();
        assertThat(turno.getPase().getToken()).isEqualTo("tanda-1:42");
        assertThat(turno.isYaEstabaEnFila()).isTrue();

        verify(filaRepository, never()).registrar(7L, 42L);
    }

    @Test
    void consultar_siElClienteTienePase_devuelveHabilitadoSinPosicion() {
        when(paseRepository.buscar(7L, 42L))
                .thenReturn(Optional.of(pase()));
        when(filaRepository.totalEnFila(7L)).thenReturn(4L);

        PosicionFilaResponseDTO respuesta = service.consultar(7L, 42L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoFila.HABILITADO);
        assertThat(respuesta.getPosicion()).isNull();
        assertThat(respuesta.getTotalEnFila()).isEqualTo(4L);
        assertThat(respuesta.getPase()).isNotNull();

        verify(filaRepository, never()).consultar(7L, 42L);
    }

    @Test
    void consultar_siElClienteEstaEsperando_devuelveEstadoEnEspera() {
        when(paseRepository.buscar(7L, 42L)).thenReturn(Optional.empty());
        when(filaRepository.consultar(7L, 42L))
                .thenReturn(Optional.of(new FilaRedisRepository.Posicion(3, 2, 5)));

        PosicionFilaResponseDTO respuesta = service.consultar(7L, 42L);

        assertThat(respuesta.getEstado()).isEqualTo(EstadoFila.EN_ESPERA);
        assertThat(respuesta.getPosicion()).isEqualTo(3L);
        assertThat(respuesta.getPase()).isNull();
    }

    private PaseRedisRepository.Pase pase() {
        return new PaseRedisRepository.Pase(
                42L, "tanda-1:42", "tanda-1", 100L,
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(301_000));
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
