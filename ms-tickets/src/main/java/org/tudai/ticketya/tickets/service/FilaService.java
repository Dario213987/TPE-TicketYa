package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FilaService {

    static final String PREFIJO_CLAVE = "tickets:fila:evento:";

    private final DisponibilidadEventoRepository eventoRepository;
    private final StringRedisTemplate redisTemplate;

    public TurnoFilaResponseDTO entrar(Long eventoId, Long clienteId) {
        if (eventoId == null) {
            throw new BadRequestException("El id del evento es obligatorio");
        }
        if (clienteId == null) {
            throw new BadRequestException("El cliente es obligatorio");
        }

        DisponibilidadEvento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "No hay disponibilidad para el evento " + eventoId));

        if (!evento.isAltaDemanda()) {
            throw new ConflictException(
                    "El evento " + eventoId + " no es de alta demanda");
        }

        String clave = PREFIJO_CLAVE + eventoId;
        String miembro = String.valueOf(clienteId);
        ZSetOperations<String, String> fila = redisTemplate.opsForZSet();

        Boolean agregado = fila.addIfAbsent(clave, miembro, System.currentTimeMillis());
        Long posicion = fila.rank(clave, miembro);
        Long total = fila.zCard(clave);
        Double score = fila.score(clave, miembro);

        if (posicion == null || total == null || score == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el turno del cliente " + clienteId
                            + " para el evento " + eventoId);
        }

        return TurnoFilaResponseDTO.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .posicion(posicion + 1)
                .totalEnFila(total)
                .yaEstabaEnFila(!Boolean.TRUE.equals(agregado))
                .ingresadoEl(Instant.ofEpochMilli(score.longValue()))
                .build();
    }
}
