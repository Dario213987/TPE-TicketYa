package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.config.ReservaProperties;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.ReservaRequestDTO;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final List<EstadoTicket> ESTADOS_VENDIDOS =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO);

    private final DisponibilidadEventoRepository eventoRepository;
    private final DisponibilidadSectorRepository sectorRepository;
    private final DisponibilidadAsientoRepository asientoRepository;
    private final TicketRepository ticketRepository;
    private final ReservaVencidaService reservaVencidaService;
    private final ReservaProperties properties;

    @Transactional
    public ReservaResponseDTO reservar(Long eventoId, Long clienteId, ReservaRequestDTO request) {
        if (clienteId == null) {
            throw new BadRequestException("El cliente es obligatorio");
        }
        if (request == null) {
            throw new BadRequestException("La reserva es obligatoria");
        }
        if (request.getSectorId() == null) {
            throw new BadRequestException("El id del sector es obligatorio");
        }
        if (request.getCantidad() == null || request.getCantidad() < 1) {
            throw new BadRequestException("La cantidad de entradas debe ser mayor a 0");
        }

        Instant ahora = Instant.now();

        DisponibilidadEvento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "No hay disponibilidad para el evento " + eventoId));

        reservaVencidaService.liberarVencidas(eventoId, ahora);

        DisponibilidadSector sector = sectorRepository
                .findByEvento_EventoIdAndSectorIdExterno(eventoId, request.getSectorId())
                .orElseThrow(() -> new NotFoundException(
                        "No existe el sector " + request.getSectorId()
                                + " para el evento " + eventoId));

        int cantidad = request.getCantidad();
        int limite = limiteDe(evento);

        long vendidas = ticketRepository.countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
                eventoId, clienteId, ESTADOS_VENDIDOS);
        long reservadasVigentes = ticketRepository.contarReservasVigentes(
                eventoId, clienteId, EstadoTicket.RESERVADO, ahora);
        long vigentes = vendidas + reservadasVigentes;

        if (vigentes + cantidad > limite) {
            throw new ConflictException(
                    "El cliente " + clienteId + " supera el limite de " + limite
                            + " entradas para el evento " + eventoId
                            + " (vigentes: " + vigentes + ", nuevas: " + cantidad + ")");
        }

        List<DisponibilidadAsiento> asientos = reservarCapacidad(sector, cantidad);
        Instant expiraEl = ahora.plus(expiracion());

        List<Ticket> tickets = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            DisponibilidadAsiento asiento = asientos == null ? null : asientos.get(i);
            tickets.add(Ticket.builder()
                    .codigo_qr(UUID.randomUUID().toString())
                    .estadoTicket(EstadoTicket.RESERVADO)
                    .clienteId(clienteId)
                    .expiraEl(expiraEl)
                    .evento(evento)
                    .sector(sector)
                    .asiento(asiento)
                    .build());
        }
        ticketRepository.saveAll(tickets);

        return ReservaResponseDTO.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .sectorId(request.getSectorId())
                .cantidad(cantidad)
                .limiteEntradasPorCliente(limite)
                .entradasVigentes(vigentes + cantidad)
                .expiraEl(expiraEl)
                .codigosQr(tickets.stream().map(Ticket::getCodigo_qr).toList())
                .build();
    }

    private int limiteDe(DisponibilidadEvento evento) {
        Integer limite = evento.getLimiteEntradasPorCliente();
        if (limite == null || limite < 1) {
            return properties.getLimitePorCliente();
        }
        return limite;
    }

    private Duration expiracion() {
        Duration expiracion = properties.getExpiracion();
        if (expiracion == null || expiracion.isZero() || expiracion.isNegative()) {
            return Duration.ofMinutes(15);
        }
        return expiracion;
    }

    private List<DisponibilidadAsiento> reservarCapacidad(DisponibilidadSector sector, int cantidad) {
        if (sector.getTipo() == TipoSector.NUMERADO) {
            List<DisponibilidadAsiento> libres = asientoRepository
                    .findBySector_IdAndEstadoOrderByFilaAscNumeroAsc(
                            sector.getId(), EstadoDisponibilidad.LIBRE);

            if (libres.size() < cantidad) {
                throw new ConflictException(
                        "No hay asientos suficientes en el sector " + sector.getSectorIdExterno());
            }

            List<DisponibilidadAsiento> elegidos = new ArrayList<>(libres.subList(0, cantidad));
            elegidos.forEach(asiento -> asiento.setEstado(EstadoDisponibilidad.RESERVADO));
            sector.setDisponibles(sector.getDisponibles() - cantidad);
            return elegidos;
        }

        if (sector.getDisponibles() < cantidad) {
            throw new ConflictException(
                    "No hay entradas suficientes en el sector " + sector.getSectorIdExterno());
        }

        sector.setDisponibles(sector.getDisponibles() - cantidad);
        return null;
    }
}
