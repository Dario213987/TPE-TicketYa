package org.tudai.ticketya.preferencias.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.tudai.ticketya.preferencias.event.EventoTicketUtilizado;
import org.tudai.ticketya.preferencias.event.EventoVentaPagada;
import org.tudai.ticketya.preferencias.service.HistorialService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HistorialEventoListenerTest {

    @Mock
    private HistorialService historialService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MapRecord<String, String, String> record;

    private HistorialProperties properties;
    private HistorialEventoListener listener;

    @BeforeEach
    void setUp() {
        properties = new HistorialProperties();
        listener = new HistorialEventoListener(historialService, redisTemplate, properties);
    }

    @Test
    void ventaPagada_procesaElEventoYGuardaLaMarcaDeIdempotencia() {
        dadoMensaje("venta.pagada", Map.of(
                "clienteId", "7",
                "eventoId", "5",
                "tipo", "RECITAL",
                "participanteIds", "15,20",
                "ciudad", "Tandil",
                "fechaEvento", "2026-12-01T20:00:00Z",
                "traceId", "trace-1"));
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        listener.onMessage(record);

        ArgumentCaptor<EventoVentaPagada> captor = ArgumentCaptor.forClass(EventoVentaPagada.class);
        verify(historialService).registrarCompra(captor.capture());
        assertThat(captor.getValue().clienteId()).isEqualTo(7L);
        assertThat(captor.getValue().eventoId()).isEqualTo(5L);
        assertThat(captor.getValue().participanteIds()).containsExactly(15L, 20L);
        assertThat(captor.getValue().ciudad()).isEqualTo("Tandil");
        assertThat(captor.getValue().fechaEvento()).isEqualTo(Instant.parse("2026-12-01T20:00:00Z"));
        assertThat(captor.getValue().traceId()).isEqualTo("trace-1");

        verify(valueOperations).set(
                eq("preferencias:historial:procesados:venta.pagada:1-0"),
                eq("1"),
                eq(Duration.ofDays(7)));
    }

    @Test
    void ticketUtilizado_procesaElUso() {
        dadoMensaje("ticket.utilizado", Map.of(
                "clienteId", "7",
                "eventoId", "5",
                "ticketId", "99",
                "fechaUso", "2026-12-02T21:00:00Z",
                "traceId", "trace-2"));
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        listener.onMessage(record);

        ArgumentCaptor<EventoTicketUtilizado> captor =
                ArgumentCaptor.forClass(EventoTicketUtilizado.class);
        verify(historialService).registrarUso(captor.capture());
        assertThat(captor.getValue().clienteId()).isEqualTo(7L);
        assertThat(captor.getValue().eventoId()).isEqualTo(5L);
        assertThat(captor.getValue().ticketId()).isEqualTo(99L);
        assertThat(captor.getValue().fechaUso()).isEqualTo(Instant.parse("2026-12-02T21:00:00Z"));
    }

    @Test
    void mensajeRepetido_noVuelveAProcesar() {
        given(record.getStream()).willReturn("venta.pagada");
        given(record.getId()).willReturn(RecordId.of("1-0"));
        given(redisTemplate.hasKey(anyString())).willReturn(true);

        listener.onMessage(record);

        verifyNoInteractions(historialService);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void mensajeSinCampoObligatorio_noFallaNiProcesa() {
        dadoMensaje("venta.pagada", Map.of("clienteId", "7", "tipo", "RECITAL"));
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        listener.onMessage(record);

        verifyNoInteractions(historialService);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void tipoDesconocido_noFallaNiProcesa() {
        dadoMensaje("venta.pagada", Map.of(
                "clienteId", "7", "eventoId", "5", "tipo", "NO_EXISTE"));
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        listener.onMessage(record);

        verifyNoInteractions(historialService);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void streamDesconocido_noProcesa() {
        given(record.getStream()).willReturn("otro.stream");
        given(record.getId()).willReturn(RecordId.of("1-0"));
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        listener.onMessage(record);

        verifyNoInteractions(historialService);
        verify(redisTemplate, never()).opsForValue();
    }

    private void dadoMensaje(String stream, Map<String, String> campos) {
        given(record.getStream()).willReturn(stream);
        given(record.getId()).willReturn(RecordId.of("1-0"));
        given(record.getValue()).willReturn(campos);
    }
}
