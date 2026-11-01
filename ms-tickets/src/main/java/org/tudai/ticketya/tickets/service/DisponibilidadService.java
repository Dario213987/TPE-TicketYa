package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.*;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoBatchRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private final DisponibilidadEventoRepository eventoRepository;
    private final DisponibilidadSectorRepository sectorRepository;
    private final DisponibilidadAsientoBatchRepository asientoBatchRepository;

    @Value("${tickets.disponibilidad.batch-size:1000}")
    private int batchSize;

    @Transactional
    public DisponibilidadResponseDTO publicar(PublicarEventoRequestDTO request) {
        Instant ahora = Instant.now();

        int eventoInsertado = eventoRepository.insertarSiNoExiste(request.getEventoId(), ahora);
        if (eventoInsertado == 0) {
            Instant creadoEl = eventoRepository.findById(request.getEventoId())
                    .map(DisponibilidadEvento::getCreadoEl)
                    .orElse(ahora);

            return DisponibilidadResponseDTO.builder()
                    .eventoId(request.getEventoId())
                    .yaExistia(true)
                    .creadoEl(creadoEl)
                    .build();
        }

        DisponibilidadEvento evento = eventoRepository.getReferenceById(request.getEventoId());

        int sectoresProcesados = 0;
        int asientosInsertados = 0;
        int asientosOmitidos = 0;

        for (SectorDisponibilidadDTO dto : request.getSectores()) {
            if (sectorRepository.findByEvento_EventoIdAndSectorIdExterno(
                    request.getEventoId(), dto.getSectorId()).isPresent()) {
                continue;
            }

            List<AsientoDTO> asientos = dto.getTipo() == TipoSector.NUMERADO
                    ? obtenerAsientos(dto)
                    : null;

            if (dto.getTipo() == TipoSector.NUMERADO && asientos.isEmpty()) {
                throw new BadRequestException(
                        "El sector numerado " + dto.getSectorId() + " no tiene asientos");
            }

            int capacidad = asientos == null ? dto.getCapacidadMaxima() : asientos.size();

            DisponibilidadSector sector = DisponibilidadSector.builder()
                    .evento(evento)
                    .sectorIdExterno(dto.getSectorId())
                    .tipo(dto.getTipo())
                    .capacidadTotal(capacidad)
                    .disponibles(capacidad)
                    .build();

            sectorRepository.save(sector);
            sectoresProcesados++;

            if (asientos != null) {
                List<DisponibilidadAsiento> entidades = asientos.stream()
                        .map(asiento -> DisponibilidadAsiento.builder()
                                .evento(evento)
                                .sector(sector)
                                .fila(asiento.getFila())
                                .numero(asiento.getNumero())
                                .estado(EstadoDisponibilidad.LIBRE)
                                .build())
                        .toList();

                int insertados = asientoBatchRepository.insertarLote(entidades, batchSize);
                asientosInsertados += insertados;
                asientosOmitidos += entidades.size() - insertados;
            }
        }

        return DisponibilidadResponseDTO.builder()
                .eventoId(request.getEventoId())
                .yaExistia(false)
                .sectoresProcesados(sectoresProcesados)
                .asientosInsertados(asientosInsertados)
                .asientosOmitidos(asientosOmitidos)
                .creadoEl(ahora)
                .build();
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
