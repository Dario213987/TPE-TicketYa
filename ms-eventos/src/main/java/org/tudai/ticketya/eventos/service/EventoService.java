package org.tudai.ticketya.eventos.service;

import feign.RetryableException;
import lombok.RequiredArgsConstructor;
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
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.dto.evento.EventoPublicadoDTO;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.repository.EventoRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final TicketsClient ticketsClient;

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
            throw new RemoteServiceException(503, "ms-tickets no disponible");
        }

        evento.setEstado(estadoPublicado);
        Evento guardado = eventoRepository.save(evento);
        return toPublicadoDTO(guardado);
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
}
