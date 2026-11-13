package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.config.ReservaProperties;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.AsientoDisponibilidadDTO;
import org.tudai.ticketya.tickets.dto.AsientoDTO;
import org.tudai.ticketya.tickets.dto.ReservaRequestDTO;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadAsientoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final List<EstadoTicket> ESTADOS_VENDIDOS =
            List.of(EstadoTicket.PENDIENTE_PAGO, EstadoTicket.EMITIDO, EstadoTicket.VENDIDO,
                    EstadoTicket.USADO);

    private final DisponibilidadEventoRepository eventoRepository;
    private final DisponibilidadSectorRepository sectorRepository;
    private final DisponibilidadAsientoRepository asientoRepository;
    private final TicketRepository ticketRepository;
    private final ReservaVencidaService reservaVencidaService;
    private final ReservaProperties properties;
    private final ReservaRepository reservaRepository;

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

        List<AsientoDTO> asientosSolicitados = asientosSolicitados(request);
        int cantidad = asientosSolicitados == null
                ? cantidadSolicitada(request)
                : asientosSolicitados.size();

        Instant ahora = Instant.now();

        DisponibilidadEvento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new ConflictException(
                        "El evento " + eventoId + " no está publicado"));

        reservaVencidaService.liberarVencidas(eventoId, ahora);

        DisponibilidadSector sector = sectorRepository
                .findByEvento_EventoIdAndSectorIdExterno(eventoId, request.getSectorId())
                .orElseThrow(() -> new NotFoundException(
                        "No existe el sector " + request.getSectorId()
                                + " para el evento " + eventoId));

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

        List<DisponibilidadAsiento> asientos = asientosSolicitados == null
                ? reservarCapacidad(sector, cantidad)
                : reservarAsientosEspecificos(sector, asientosSolicitados);

        Instant expiraEl = ahora.plus(expiracion());

        Reserva reserva = reservaRepository.save(Reserva.builder()
                .eventoId(eventoId)
                .clienteId(clienteId)
                .sectorIdExterno(request.getSectorId())
                .expiraEl(expiraEl)
                .creadoEl(ahora)
                .build());

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
                    .reserva(reserva)
                    .build());
        }

        try {
            ticketRepository.saveAll(tickets);
            ticketRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Alguno de los asientos solicitados ya no está disponible");
        }

        return ReservaResponseDTO.builder()
                .idReserva(reserva.getId())
                .eventoId(eventoId)
                .clienteId(clienteId)
                .sectorId(request.getSectorId())
                .cantidad(cantidad)
                .limiteEntradasPorCliente(limite)
                .entradasVigentes(vigentes + cantidad)
                .expiraEl(expiraEl)
                .codigosQr(tickets.stream().map(Ticket::getCodigo_qr).toList())
                .asientos(asientos == null ? null : asientos.stream()
                        .map(asiento -> AsientoDisponibilidadDTO.builder()
                                .fila(asiento.getFila())
                                .numero(asiento.getNumero())
                                .estado(asiento.getEstado())
                                .build())
                        .toList())
                .build();
    }

    private int cantidadSolicitada(ReservaRequestDTO request) {
        if (request.getCantidad() == null || request.getCantidad() < 1) {
            throw new BadRequestException("La cantidad de entradas debe ser mayor a 0");
        }
        return request.getCantidad();
    }

    private List<AsientoDTO> asientosSolicitados(ReservaRequestDTO request) {
        if (!request.tieneAsientosEspecificos()) {
            return null;
        }

        Set<String> unicos = new HashSet<>();
        List<AsientoDTO> resultado = new ArrayList<>(request.getAsientos().size());

        for (AsientoDTO asiento : request.getAsientos()) {
            if (asiento == null || asiento.getFila() == null || asiento.getFila() < 1
                    || asiento.getNumero() == null || asiento.getNumero() < 1) {
                throw new BadRequestException(
                        "La fila y el numero de asiento deben ser mayores a 0");
            }
            if (!unicos.add(asiento.getFila() + "-" + asiento.getNumero())) {
                throw new BadRequestException(
                        "El asiento " + asiento.getFila() + "-" + asiento.getNumero()
                                + " está repetido en la reserva");
            }
            resultado.add(AsientoDTO.builder()
                    .fila(asiento.getFila())
                    .numero(asiento.getNumero())
                    .build());
        }

        resultado.sort(Comparator
                .comparing(AsientoDTO::getFila)
                .thenComparing(AsientoDTO::getNumero));
        return resultado;
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
            return Duration.ofMinutes(10);
        }
        return expiracion;
    }

    private List<DisponibilidadAsiento> reservarAsientosEspecificos(
            DisponibilidadSector sector, List<AsientoDTO> solicitados) {
        if (sector.getTipo() != TipoSector.NUMERADO) {
            throw new BadRequestException(
                    "El sector " + sector.getSectorIdExterno() + " no es numerado");
        }

        List<DisponibilidadAsiento> asientos = new ArrayList<>(solicitados.size());
        for (AsientoDTO solicitado : solicitados) {
            DisponibilidadAsiento asiento = asientoRepository
                    .findBySector_IdAndFilaAndNumero(
                            sector.getId(), solicitado.getFila(), solicitado.getNumero())
                    .orElseThrow(() -> new ConflictException(
                            "El asiento " + solicitado.getFila() + "-" + solicitado.getNumero()
                                    + " no está disponible"));

            if (asiento.getEstado() != EstadoDisponibilidad.LIBRE) {
                throw new ConflictException(
                        "El asiento " + solicitado.getFila() + "-" + solicitado.getNumero()
                                + " no está disponible");
            }

            asientos.add(asiento);
        }

        if (sector.getDisponibles() < asientos.size()) {
            throw new ConflictException(
                    "No hay asientos suficientes en el sector " + sector.getSectorIdExterno());
        }

        asientos.forEach(asiento -> asiento.setEstado(EstadoDisponibilidad.RESERVADO));
        sector.setDisponibles(sector.getDisponibles() - asientos.size());
        return asientos;
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
