package org.tudai.ticketya.eventos.service;

import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.client.dto.AsientoRequest;
import org.tudai.ticketya.eventos.client.dto.PublicarEventoRequest;
import org.tudai.ticketya.eventos.client.dto.SectorDisponibilidadRequest;
import org.tudai.ticketya.eventos.client.dto.TipoSectorRequest;
import org.tudai.ticketya.eventos.domain.Asiento;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.dto.evento.EventoPublicadoDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoUpdateDTO;
import org.tudai.ticketya.eventos.dto.evento.PrecioEventoSectorResponseDTO;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final TicketsClient ticketsClient;
    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;

    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(Pageable pageable) {
        return eventoRepository.findByEstadoIn(EstadoEvento.VISIBLES, pageable);
    }

    @Transactional
    public EventoPublicadoDTO publicarEvento(Long idEvento, Long organizadorId, boolean esAdmin) {

        //Busco el evento existente como borrador
        Evento evento = eventoRepository.findById(idEvento)
                .orElseThrow(() -> new NotFoundException("Evento " + idEvento + " no existe"));

        //Verifico que tenga el permiso para publicar evento
        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés publicar un evento que no te pertenece");
        }

        //Si el evento ya esta publicado, lo retorno (no lo creo de nuevo)
        EstadoEvento estadoPublicado = estadoPublicado();
        if (evento.getEstado() == estadoPublicado) {
            return toPublicadoDTO(evento);
        }

        //Verifico que si este en borrador
        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden publicar eventos en estado BORRADOR");
        }

        if (evento.getFecha() == null || !evento.getFecha().isAfter(LocalDate.now())) {
            throw new BadRequestException("La fecha del evento debe ser posterior a hoy");
        }

        if (evento.getLugar() == null) {
            throw new BadRequestException("El evento no tiene un lugar asociado");
        }

        //Verificacion de errores de sector. Si algun sector no tiene un precio, se crea dentro de una exception
        //especifica.

        Set<Sector> sectores = evento.getLugar().getSectores();
        if (sectores == null || sectores.isEmpty()) {
            throw new BadRequestException("El lugar del evento no tiene sectores");
        }

        List<SectoresSinPrecioException.SectorFaltante> faltantes = sectores.stream()
                .filter(sector -> sector.getPrecio() == null)
                .sorted(Comparator.comparing(Sector::getId))
                .map(sector -> new SectoresSinPrecioException.SectorFaltante(
                        sector.getId(), sector.getNombre()))
                .toList();

        //Si la lista de los sectores con error no esta vacia, tira la exepcion.
        if (!faltantes.isEmpty()) {
            throw new SectoresSinPrecioException(faltantes);
        }

        try {
            ticketsClient.crearDisponibilidad(evento.getId(), buildInventarioRequest(evento, sectores));
        } catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");
        }

        evento.setEstado(estadoPublicado);
        Evento guardado = eventoRepository.save(evento);
        return toPublicadoDTO(guardado);
    }

    @Transactional
    public EventoResponseDTO actualizarEvento(Long eventoId, EventoUpdateDTO dto, Long organizadorId,
                                              boolean esAdmin) {
        Evento evento = buscarEvento(eventoId);
        verificarPropiedad(evento, organizadorId, esAdmin, "modificar");

        validarCamposEstructurales(evento, dto);
        aplicarCambios(evento, dto);

        return toResponse(eventoRepository.save(evento));
    }

    @Transactional
    public void eliminarEvento(Long eventoId, Long organizadorId, boolean esAdmin) {
        Evento evento = buscarEvento(eventoId);
        verificarPropiedad(evento, organizadorId, esAdmin, "eliminar");

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden eliminar eventos en estado BORRADOR");
        }

        eventoRepository.delete(evento);
    }

    private Evento buscarEvento(Long eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento no encontrado con id: " + eventoId));
    }

    private void verificarPropiedad(Evento evento, Long organizadorId, boolean esAdmin, String accion) {
        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés " + accion + " un evento que no te pertenece");
        }
    }

    private void validarCamposEstructurales(Evento evento, EventoUpdateDTO dto) {
        boolean esBorrador = evento.getEstado() == EstadoEvento.BORRADOR;

        if (dto.getFecha() != null && !Objects.equals(dto.getFecha(), evento.getFecha())) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se puede modificar la fecha de un evento ya publicado. Cancelalo o reprogramalo.");
            }
            if (!dto.getFecha().isAfter(LocalDate.now())) {
                throw new BadRequestException("La fecha del evento debe ser posterior a hoy");
            }
        }

        if (dto.getLugarId() != null && (evento.getLugar() == null
                || !Objects.equals(dto.getLugarId(), evento.getLugar().getId()))) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se puede modificar el lugar de un evento ya publicado. Cancelalo o reprogramalo.");
            }
        }

        if (dto.getSectores() != null && !Objects.equals(dto.getSectores(), sectoresDelEvento(evento))) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se pueden modificar los sectores de un evento ya publicado. Cancelalo o reprogramalo.");
            }
            throw new BadRequestException(
                    "Los sectores de un evento se administran desde el lugar asociado");
        }
    }

    private void aplicarCambios(Evento evento, EventoUpdateDTO dto) {
        if (dto.getNombre() != null) {
            if (dto.getNombre().isBlank()) {
                throw new BadRequestException("El nombre del evento no puede estar vacío");
            }
            evento.setNombre(dto.getNombre().trim());
        }

        if (dto.getDescripcion() != null) {
            if (dto.getDescripcion().isBlank()) {
                throw new BadRequestException("La descripción del evento no puede estar vacía");
            }
            evento.setDescripcion(dto.getDescripcion().trim());
        }

        if (dto.getTipo() != null) {
            if (dto.getTipo().isBlank()) {
                throw new BadRequestException("El tipo del evento no puede estar vacío");
            }
            evento.setTipo(dto.getTipo().trim());
        }

        if (dto.getFecha() != null) {
            evento.setFecha(dto.getFecha());
        }

        if (dto.getLugarId() != null && (evento.getLugar() == null
                || !Objects.equals(dto.getLugarId(), evento.getLugar().getId()))) {
            Lugar lugar = lugarRepository.findById(dto.getLugarId())
                    .orElseThrow(() -> new NotFoundException(
                            "Lugar no encontrado con id: " + dto.getLugarId()));
            evento.setLugar(lugar);
        }
    }

    private Set<Long> sectoresDelEvento(Evento evento) {
        Lugar lugar = evento.getLugar();
        if (lugar == null || lugar.getSectores() == null) {
            return Set.of();
        }
        return lugar.getSectores().stream()
                .map(Sector::getId)
                .collect(Collectors.toSet());
    }

    private EventoResponseDTO toResponse(Evento evento) {
        return modelMapper.map(evento, EventoResponseDTO.class);
    }

    @Transactional
    public PrecioEventoSectorResponseDTO definirPrecioSector(Long eventoId, Long sectorId, Double precio,
                                                             Long organizadorId, boolean esAdmin) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento no encontrado con id: " + eventoId));

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés definir precios en un evento que no te pertenece");
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden definir precios en eventos en estado BORRADOR");
        }

        if (precio == null || precio <= 0) {
            throw new BadRequestException("El precio debe ser mayor a 0");
        }

        if (!sectorPerteneceAlLugar(evento, sectorId)) {
            throw new BadRequestException("El sector " + sectorId + " no pertenece al lugar del evento");
        }

        evento.definirPrecioSector(sectorId, precio);
        eventoRepository.save(evento);

        return PrecioEventoSectorResponseDTO.builder()
                .eventoId(eventoId)
                .sectorId(sectorId)
                .precio(precio)
                .build();
    }

    private EstadoEvento estadoPublicado() {
        try {
            return EstadoEvento.valueOf("PUBLICADO");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "El enum EstadoEvento no tiene PUBLICADO", e);
        }
    }

    private PublicarEventoRequest buildInventarioRequest(Evento evento, Set<Sector> sectores) {
        List<SectorDisponibilidadRequest> items = sectores.stream()
                .sorted(Comparator.comparing(Sector::getId))
                .map(this::toSectorDisponibilidadRequest)
                .toList();

        return PublicarEventoRequest.builder()
                .eventoId(evento.getId())
                .sectores(items)
                .build();
    }

    private SectorDisponibilidadRequest toSectorDisponibilidadRequest(Sector sector) {
        SectorDisponibilidadRequest.SectorDisponibilidadRequestBuilder builder =
                SectorDisponibilidadRequest.builder().sectorId(sector.getId());

        if (sector instanceof SectorNumerado numerado) {
            builder.tipo(TipoSectorRequest.NUMERADO)
                    .asientos(numerado.getAsientos().stream()
                            .sorted(Comparator.comparing(Asiento::getFila)
                                    .thenComparing(Asiento::getNumero))
                            .map(asiento -> AsientoRequest.builder()
                                    .fila(asiento.getFila())
                                    .numero(asiento.getNumero())
                                    .build())
                            .toList());
        } else if (sector instanceof SectorNoNumerado noNumerado) {
            builder.tipo(TipoSectorRequest.NO_NUMERADO)
                    .capacidadMaxima(noNumerado.getCapacidadMaxima());
        } else {
            throw new IllegalStateException(
                    "Tipo de sector no soportado: " + sector.getClass().getName());
        }

        return builder.build();
    }

    private EventoPublicadoDTO toPublicadoDTO(Evento evento) {
        return EventoPublicadoDTO.builder()
                .id(evento.getId())
                .nombre(evento.getNombre())
                .estado(evento.getEstado().name())
                .build();
    }

    private boolean sectorPerteneceAlLugar(Evento evento, Long sectorId) {
        Lugar lugar = evento.getLugar();
        if (lugar == null || lugar.getSectores() == null) {
            return false;
        }
        return lugar.getSectores().stream()
                .anyMatch(sector -> Objects.equals(sector.getId(), sectorId));
    }
}
