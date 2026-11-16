package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.tickets.client.EventoClient;
import org.tudai.ticketya.tickets.client.dto.EventoResponseDto;
import org.tudai.ticketya.tickets.client.dto.SectorResponseDto;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.reporte.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteOcupacionDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteOcupacionSectorDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteEntradasSectorDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.ReporteRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final List<EstadoTicket> ESTADOS_VENDIDOS =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO);

    private static final List<EstadoTicket> ESTADOS_REPORTE_ENTRADAS =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO);

    private final EventoClient eventoClient;
    private final ReporteRepository reporteRepository;
    private final DisponibilidadSectorRepository disponibilidadSectorRepository;

    /**
     * Reporte de entradas vendidas y devueltas por sector
     * */
    @Transactional(readOnly = true)
    public ReporteEntradasVendidasDTO obtenerReporteEntradasVendidas(
            Long eventoId, Long solicitanteId, boolean esAdmin) {

        EventoResponseDto evento = eventoClient.obtenerEvento(eventoId);

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), solicitanteId)) {
            throw new ForbiddenException("No tenés permiso para consultar el reporte de este evento");
        }

        // Mapa de resumen por secor, la clave es el id del sector y el objeto el resumen
        Map<Long, Object[]> resumenPorSector = new HashMap<>();

        // Llama a la query del repository y recorre el resultado
        for (Object[] fila : reporteRepository.resumirPorSector(
                eventoId, ESTADOS_VENDIDOS, EstadoTicket.DEVUELTO, ESTADOS_REPORTE_ENTRADAS)) {
            if (fila[0] == null) {
                continue;
            }

            // Guarda la fila usando el sectorId como clave
            resumenPorSector.put(((Number) fila[0]).longValue(), fila);
        }

        // Relaciona cada sector del evento con su resumen

        List<ReporteEntradasSectorDTO> sectores = evento.getSectores() == null
                ? List.of()
                : evento.getSectores().stream()
                .map(sector -> aReporteSector(sector, resumenPorSector.get(sector.getId())))
                .toList();

        // Obtiene el total de entradas vendidas
        long entradasVendidas = sectores.stream()
                .mapToLong(ReporteEntradasSectorDTO::getEntradasVendidas)
                .sum();

        // Obtiene el total de entradas devueltas
        long entradasDevueltas = sectores.stream()
                .mapToLong(ReporteEntradasSectorDTO::getEntradasDevueltas)
                .sum();

        // Retorna reporte completo
        return ReporteEntradasVendidasDTO.builder()
                .eventoId(evento.getId())
                .eventoNombre(evento.getNombre())
                .eventoFecha(evento.getFecha())
                .eventoEstado(evento.getEstado())
                .entradasVendidas(entradasVendidas)
                .entradasDevueltas(entradasDevueltas)
                .sectores(sectores)
                .build();
    }

    /**
     * Reporte de ocupación por sector y total del evento.
     * Ocupación = entradas vendidas / capacidad del sector * 100.
     * */
    @Transactional(readOnly = true)
    public ReporteOcupacionDTO obtenerReporteOcupacion(Long eventoId, Long solicitanteId, boolean esAdmin) {

        EventoResponseDto evento = eventoClient.obtenerEvento(eventoId);

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), solicitanteId)) {
            throw new ForbiddenException("No tenés permiso para consultar el reporte de este evento");
        }

        // Capacidad total de cada sector, la clave es el id del sector
        Map<Long, Integer> capacidades = disponibilidadSectorRepository
                .findByEvento_EventoIdOrderByIdAsc(eventoId).stream()
                .collect(Collectors.toMap(
                        DisponibilidadSector::getSectorIdExterno,
                        sector -> sector.getCapacidadTotal() == null ? 0 : sector.getCapacidadTotal()));

        // Entradas vendidas por sector, la clave es el id del sector
        Map<Long, Long> vendidasPorSector = new HashMap<>();

        for (Object[] fila : reporteRepository.resumirPorSector(
                eventoId, ESTADOS_VENDIDOS, EstadoTicket.DEVUELTO, ESTADOS_REPORTE_ENTRADAS)) {
            if (fila[0] == null) {
                continue;
            }

            vendidasPorSector.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }

        List<SectorResponseDto> sectoresEvento = evento.getSectores() == null
                ? List.of()
                : evento.getSectores();

        List<ReporteOcupacionSectorDTO> sectores = sectoresEvento.stream()
                .map(sector -> aOcupacionSector(sector,
                        vendidasPorSector.getOrDefault(sector.getId(), 0L),
                        capacidades.getOrDefault(sector.getId(), 0)))
                .toList();

        long totalVendidas = sectoresEvento.stream()
                .mapToLong(sector -> vendidasPorSector.getOrDefault(sector.getId(), 0L))
                .sum();

        long totalCapacidad = sectoresEvento.stream()
                .mapToLong(sector -> capacidades.getOrDefault(sector.getId(), 0))
                .sum();

        return ReporteOcupacionDTO.builder()
                .eventoId(evento.getId())
                .eventoNombre(evento.getNombre())
                .eventoFecha(evento.getFecha())
                .eventoEstado(evento.getEstado())
                .ocupacionTotal(porcentaje(totalVendidas, totalCapacidad))
                .sectores(sectores)
                .build();
    }


    private ReporteEntradasSectorDTO aReporteSector(SectorResponseDto sector, Object[] resumen) {
        long vendidas = resumen == null ? 0 : ((Number) resumen[1]).longValue();
        long devueltas = resumen == null ? 0 : ((Number) resumen[2]).longValue();

        return ReporteEntradasSectorDTO.builder()
                .sectorId(sector.getId())
                .sectorNombre(sector.getNombre())
                .entradasVendidas(vendidas)
                .entradasDevueltas(devueltas)
                .build();
    }

    private ReporteOcupacionSectorDTO aOcupacionSector(SectorResponseDto sector, long vendidas, int capacidad) {
        return ReporteOcupacionSectorDTO.builder()
                .sectorId(sector.getId())
                .sectorNombre(sector.getNombre())
                .ocupacion(porcentaje(vendidas, capacidad))
                .build();
    }

    private double porcentaje(long vendidas, long capacidad) {
        if (capacidad <= 0) {
            return 0.0;
        }
        return Math.round(((double) vendidas / capacidad) * 10000.0) / 100.0;
    }
}
