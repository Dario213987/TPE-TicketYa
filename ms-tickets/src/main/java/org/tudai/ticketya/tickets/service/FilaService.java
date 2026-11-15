package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.EstadoFila;
import org.tudai.ticketya.tickets.domain.EstadoVenta;
import org.tudai.ticketya.tickets.dto.PaseDTO;
import org.tudai.ticketya.tickets.dto.PosicionFilaResponseDTO;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.FilaRedisRepository;
import org.tudai.ticketya.tickets.repository.PaseRedisRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FilaService {

    private final DisponibilidadEventoRepository eventoRepository;
    private final FilaRedisRepository filaRepository;
    private final PaseRedisRepository paseRepository;

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

        Optional<PaseRedisRepository.Pase> paseActual =
                paseRepository.buscar(eventoId, clienteId);
        if (paseActual.isPresent()) {
            return habilitado(eventoId, clienteId, paseActual.get());
        }

        FilaRedisRepository.Registro registro = filaRepository.registrar(eventoId, clienteId);

        if (registro.yaHabilitado()) {
            PaseRedisRepository.Pase pase = paseRepository.buscar(eventoId, clienteId)
                    .orElseThrow(() -> new ConflictException(
                            "El pase del cliente " + clienteId + " para el evento "
                                    + eventoId + " venció"));
            return habilitado(eventoId, clienteId, pase);
        }

        return TurnoFilaResponseDTO.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .posicion(registro.posicion())
                .totalEnFila(registro.totalEnFila())
                .yaEstabaEnFila(!registro.agregado())
                .ingresadoEl(registro.ingresadoEl())
                .estado(EstadoFila.EN_ESPERA)
                .build();
    }

    /**
     * Consulta liviana para polling: solo lee Redis, sin tocar la base de datos.
     * Si el cliente tiene un pase devuelve HABILITADO; si está esperando, su
     * posición; si no está en ninguna de las dos estructuras responde 404.
     */
    public PosicionFilaResponseDTO consultar(Long eventoId, Long clienteId) {
        if (eventoId == null) {
            throw new BadRequestException("El id del evento es obligatorio");
        }
        if (clienteId == null) {
            throw new BadRequestException("El cliente es obligatorio");
        }

        Optional<PaseRedisRepository.Pase> pase =
                paseRepository.buscar(eventoId, clienteId);
        if (pase.isPresent()) {
            return PosicionFilaResponseDTO.builder()
                    .eventoId(eventoId)
                    .clienteId(clienteId)
                    .totalEnFila(filaRepository.totalEnFila(eventoId))
                    .estado(EstadoFila.HABILITADO)
                    .pase(pase(pase.get()))
                    .build();
        }

        FilaRedisRepository.Posicion posicion = filaRepository.consultar(eventoId, clienteId)
                .orElseThrow(() -> new NotFoundException(
                        "El cliente " + clienteId + " no está en la fila del evento " + eventoId));

        return PosicionFilaResponseDTO.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .posicion(posicion.posicion())
                .personasDelante(posicion.personasDelante())
                .totalEnFila(posicion.totalEnFila())
                .estado(EstadoFila.EN_ESPERA)
                .build();
    }

    private TurnoFilaResponseDTO habilitado(Long eventoId, Long clienteId,
                                            PaseRedisRepository.Pase pase) {
        return TurnoFilaResponseDTO.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .totalEnFila(filaRepository.totalEnFila(eventoId))
                .yaEstabaEnFila(true)
                .ingresadoEl(pase.emitidoEl())
                .estado(EstadoFila.HABILITADO)
                .pase(pase(pase))
                .build();
    }

    private PaseDTO pase(PaseRedisRepository.Pase pase) {
        return PaseDTO.builder()
                .token(pase.token())
                .tanda(pase.tanda())
                .expiraEl(pase.expiraEl())
                .build();
    }
}
