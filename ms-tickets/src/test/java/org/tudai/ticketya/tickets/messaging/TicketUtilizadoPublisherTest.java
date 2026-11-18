package org.tudai.ticketya.tickets.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.tickets.config.PreferenciasProperties;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketUtilizadoPublisherTest {

    private static final String STREAM = "ticket.utilizado";

    @Mock
    private StringRedisTemplate preferenciasRedisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private PreferenciasProperties properties;
    private TicketUtilizadoPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new PreferenciasProperties();
        properties.setEnabled(true);
        properties.setStreamTicketUtilizado(STREAM);
        publisher = new TicketUtilizadoPublisher(preferenciasRedisTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publicar_ticketValido_agregaElEventoAlStream() {
        MDC.put(MdcKeys.CORRELATION_ID, "corr-1");
        when(preferenciasRedisTemplate.opsForStream()).thenReturn(streamOperations);

        publisher.publicar(ticket(99L, 7L, 5L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(eq(STREAM), captor.capture());
        Map<String, String> campos = captor.getValue();
        assertThat(campos)
                .containsEntry("clienteId", "7")
                .containsEntry("eventoId", "5")
                .containsEntry("ticketId", "99")
                .containsEntry("traceId", "corr-1");
        assertThat(Instant.parse(campos.get("fechaUso"))).isNotNull();
    }

    @Test
    void publicar_sinCliente_noAgregaElEvento() {
        publisher.publicar(ticket(99L, null, 5L));

        verifyNoInteractions(streamOperations);
    }

    @Test
    void publicar_deshabilitado_noAgregaElEvento() {
        properties.setEnabled(false);

        publisher.publicar(ticket(99L, 7L, 5L));

        verifyNoInteractions(streamOperations);
    }

    @Test
    void publicar_sinTraceId_noAgregaElCampo() {
        when(preferenciasRedisTemplate.opsForStream()).thenReturn(streamOperations);

        publisher.publicar(ticket(99L, 7L, 5L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(eq(STREAM), captor.capture());
        assertThat(captor.getValue()).doesNotContainKey("traceId");
    }

    @Test
    void publicar_errorDeRedis_noSePropaga() {
        when(preferenciasRedisTemplate.opsForStream()).thenReturn(streamOperations);
        doThrow(new RuntimeException("redis caido"))
                .when(streamOperations).add(eq(STREAM), anyMap());

        assertThatCode(() -> publisher.publicar(ticket(99L, 7L, 5L)))
                .doesNotThrowAnyException();
    }

    private Ticket ticket(Long id, Long clienteId, Long eventoId) {
        return Ticket.builder()
                .id(id)
                .codigo_qr("qr-1")
                .clienteId(clienteId)
                .estadoTicket(EstadoTicket.USADO)
                .evento(DisponibilidadEvento.builder().eventoId(eventoId).build())
                .build();
    }
}
