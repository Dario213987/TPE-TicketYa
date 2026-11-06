package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.*;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private final DisponibilidadEventoRepository eventoRepository;
    private final DisponibilidadRepository disponibilidadRepository;
    private final DisponibilidadAsientoRepository asientoRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public DisponibilidadResponseDTO publicar(Long eventoId, PublicarEventoRequestDTO request) {
        Instant ahora = Instant.now();

        Optional<DisponibilidadEvento> existente = eventoRepository.findById(eventoId);
        if (existente.isPresent()) {
            return DisponibilidadResponseDTO.builder()
                    .eventoId(eventoId)
                    .yaExistia(true)
                    .creadoEl(existente.get().getCreadoEl())
                    .build();
        }

        DisponibilidadEvento evento = eventoRepository.save(
                DisponibilidadEvento.builder()
                        .eventoId(eventoId)
                        .creadoEl(ahora)
                        .build());

        int sectoresProcesados = 0;
        int asientosInsertados = 0;

        for (SectorDisponibilidadDTO dto : request.getSectores()) {
            if (disponibilidadRepository.findByEvento_EventoIdAndSectorIdExterno(
                    eventoId, dto.getSectorId()).isPresent()) {
                continue;
            }

            if (dto.getTipo() == TipoSector.NUMERADO) {
                List<AsientoDTO> asientos = obtenerAsientos(dto);

                if (asientos.isEmpty()) {
                    throw new BadRequestException(
                            "El sector numerado " + dto.getSectorId() + " no tiene asientos");
                }

                DisponibilidadNumerada numerada = new DisponibilidadNumerada();
                numerada.setEvento(evento);
                numerada.setSectorIdExterno(dto.getSectorId());
                disponibilidadRepository.save(numerada);
                sectoresProcesados++;

                List<DisponibilidadAsiento> entidades = asientos.stream()
                        .map(asiento -> DisponibilidadAsiento.builder()
                                .evento(evento)
                                .disponibilidad(numerada)
                                .fila(asiento.getFila())
                                .numero(asiento.getNumero())
                                .estado(EstadoDisponibilidad.LIBRE)
                                .build())
                        .toList();

                asientoRepository.saveAll(entidades);
                asientosInsertados += entidades.size();
            } else {
                if (dto.getCapacidadMaxima() == null) {
                    throw new BadRequestException(
                            "El sector no numerado " + dto.getSectorId() + " no tiene capacidad");
                }

                DisponibilidadNoNumerada noNumerada = new DisponibilidadNoNumerada();
                noNumerada.setEvento(evento);
                noNumerada.setSectorIdExterno(dto.getSectorId());
                noNumerada.setCapacidadTotal(dto.getCapacidadMaxima());
                disponibilidadRepository.save(noNumerada);
                sectoresProcesados++;
            }
        }

        return DisponibilidadResponseDTO.builder()
                .eventoId(eventoId)
                .yaExistia(false)
                .sectoresProcesados(sectoresProcesados)
                .asientosInsertados(asientosInsertados)
                .creadoEl(ahora)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean puedeReservar(Long eventoId, Long sectorIdExterno) {
        Disponibilidad disponibilidad = disponibilidadRepository
                .findByEvento_EventoIdAndSectorIdExterno(eventoId, sectorIdExterno)
                .orElseThrow(() -> new BadRequestException(
                        "No existe disponibilidad para el sector " + sectorIdExterno
                                + " del evento " + eventoId));

        if (disponibilidad instanceof DisponibilidadNoNumerada noNumerada) {
            long reservasActivas = ticketRepository.countByDisponibilidad_IdAndEstadoTicketNotIn(
                    noNumerada.getId(),
                    List.of(EstadoTicket.DEVUELTO, EstadoTicket.CANCELADO));
            return noNumerada.puedeReservar(reservasActivas);
        }

        if (disponibilidad instanceof DisponibilidadNumerada numerada) {
            return asientoRepository.countByDisponibilidad_IdAndEstado(
                    numerada.getId(), EstadoDisponibilidad.LIBRE) > 0;
        }

        return false;
    }

    @Transactional
    public void reservarAsiento(Long eventoId, Long sectorIdExterno, Integer fila, Integer numero) {
        Disponibilidad disponibilidad = disponibilidadRepository
                .findByEvento_EventoIdAndSectorIdExterno(eventoId, sectorIdExterno)
                .orElseThrow(() -> new BadRequestException(
                        "No existe disponibilidad para el sector " + sectorIdExterno
                                + " del evento " + eventoId));

        if (!(disponibilidad instanceof DisponibilidadNumerada numerada)) {
            throw new BadRequestException("El sector " + sectorIdExterno + " no es numerado");
        }

        DisponibilidadAsiento asiento = asientoRepository
                .findByDisponibilidad_IdAndFilaAndNumero(numerada.getId(), fila, numero)
                .orElseThrow(() -> new BadRequestException(
                        "No existe el asiento " + fila + "-" + numero + " en el sector " + sectorIdExterno));

        if (asiento.getEstado() != EstadoDisponibilidad.LIBRE) {
            throw new BadRequestException("El asiento " + fila + "-" + numero + " no esta disponible");
        }

        asiento.setEstado(EstadoDisponibilidad.RESERVADO);
    }

    private List<AsientoDTO> obtenerAsientos(SectorDisponibilidadDTO dto) {
        Map<String, AsientoDTO> unicos = new LinkedHashMap<>();

        if (dto.getAsientos() != null && !dto.getAsientos().isEmpty()) {
            for (AsientoDTO asiento : dto.getAsientos()) {
                unicos.putIfAbsent(clave(asiento.getFila(), asiento.getNumero()), asiento);
            }
            return new ArrayList<>(unicos.values());
        }

        if (dto.getFilas() == null || dto.getAsientosPorFila() == null) {
            return List.of();
        }

        for (int fila = 1; fila <= dto.getFilas(); fila++) {
            for (int numero = 1; numero <= dto.getAsientosPorFila(); numero++) {
                unicos.putIfAbsent(clave(fila, numero), AsientoDTO.builder()
                        .fila(fila)
                        .numero(numero)
                        .build());
            }
        }

        return new ArrayList<>(unicos.values());
    }

    private String clave(Integer fila, Integer numero) {
        return fila + "-" + numero;
    }
}
