package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.tickets.client.EventoClient;
import org.tudai.ticketya.tickets.client.dto.EventoResponseDto;
import org.tudai.ticketya.tickets.client.dto.SectorResponseDto;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.dto.ReporteSectorDTO;
import org.tudai.ticketya.tickets.repository.ReporteRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final List<EstadoTicket> ESTADOS_VENDIDOS =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO);

    private static final List<EstadoTicket> ESTADOS_REPORTE =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO);

    private final EventoClient eventoClient;
    private final ReporteRepository reporteRepository;

    @Transactional(readOnly = true)
    public ReporteEntradasVendidasDTO obtenerReporteEntradasVendidas(
            long eventoId, Long solicitanteId, boolean esAdmin) {

        EventoResponseDto evento = eventoClient.obtenerEvento(eventoId);

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), solicitanteId)) {
            throw new ForbiddenException("No tenés permiso para consultar el reporte de este evento");
        }

        // Mapa de resumen por secor, la clave es el id del sector y el objeto el resumen
        Map<Long, Object[]> resumenPorSector = new HashMap<>();

        // Llama a la query del repository y recorre el resultado
        for (Object[] fila : reporteRepository.resumirPorSector(
                eventoId, ESTADOS_VENDIDOS, EstadoTicket.DEVUELTO, ESTADOS_REPORTE)) {
            if (fila[0] == null) {
                continue;
            }

            // Guarda la fila usando el sectorId como clave
            resumenPorSector.put(((Number) fila[0]).longValue(), fila);
        }

        // Relaciona cada sector del evento con su resumen

        List<ReporteSectorDTO> sectores = evento.getSectores() == null
                ? List.of()
                : evento.getSectores().stream()
                .map(sector -> aReporteSector(sector, resumenPorSector.get(sector.getId())))
                .toList();

        // Obtiene el total de entradas vendidas
        long entradasVendidas = sectores.stream()
                .mapToLong(ReporteSectorDTO::getEntradasVendidas)
                .sum();

        // Obtiene el total de entradas devueltas
        long entradasDevueltas = sectores.stream()
                .mapToLong(ReporteSectorDTO::getEntradasDevueltas)
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
     * Recibe los datos de un sector para realizar el reporte.
     * Si algun valor viene nulo, se vuelve 0
     * */

    private ReporteSectorDTO aReporteSector(SectorResponseDto sector, Object[] resumen) {
        long vendidas = resumen == null ? 0 : ((Number) resumen[1]).longValue();
        long devueltas = resumen == null ? 0 : ((Number) resumen[2]).longValue();

        return ReporteSectorDTO.builder()
                .sectorId(sector.getId())
                .sectorNombre(sector.getNombre())
                .entradasVendidas(vendidas)
                .entradasDevueltas(devueltas)
                .build();
    }
}
