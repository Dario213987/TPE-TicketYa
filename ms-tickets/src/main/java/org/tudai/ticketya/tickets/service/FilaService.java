package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoVenta;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.FilaRedisRepository;

@Service
@RequiredArgsConstructor
public class FilaService {

    private final DisponibilidadEventoRepository eventoRepository;
    private final FilaRedisRepository filaRepository;

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

        if (evento.getEstadoVenta() != EstadoVenta.ABIERTA) {
            throw new ConflictException(
                    "La venta del evento " + eventoId + " no está abierta ("
                            + evento.getEstadoVenta() + ")");
        }

        FilaRedisRepository.Registro registro = filaRepository.registrar(eventoId, clienteId);

        return TurnoFilaResponseDTO.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .posicion(registro.posicion())
                .totalEnFila(registro.totalEnFila())
                .yaEstabaEnFila(!registro.agregado())
                .ingresadoEl(registro.ingresadoEl())
                .build();
    }
}
