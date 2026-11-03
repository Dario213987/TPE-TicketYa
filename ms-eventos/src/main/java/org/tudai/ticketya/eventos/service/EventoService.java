package org.tudai.ticketya.eventos.service;

import feign.FeignException;
import feign.RetryableException;
import jakarta.persistence.EntityNotFoundException;
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
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
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


    /**
     * Publica un evento que está en estado BORRADOR.
     * <p>
     * Valida que el evento exista, que el usuario tenga permiso (organizador dueño o administrador),
     * que la fecha sea posterior a hoy(momento en que se solicita el cambio),
     * que tenga un lugar con sectores y que todos los sectores
     * tengan precio. Si las validaciones pasan, crea el inventario en ms-tickets y recién después
     * cambia el estado a PUBLICADO.
     * <p>
     * Es idempotente: si el evento ya estaba publicado, devuelve su información sin volver
     * a llamar a ms-tickets.
     *
     * @param idEvento       id del evento a publicar
     * @param organizadorId  id del usuario que realiza la solicitud
     * @param esAdmin        {@code true} si el usuario es administrador (puede publicar cualquier evento)
     *
     * @return DTO con la información del evento publicado
     *
     * @throws NotFoundException           si el evento no existe
     * @throws ForbiddenException          si el usuario no es admin y el evento no le pertenece
     * @throws ConflictException           si el evento no está en estado BORRADOR
     * @throws BadRequestException         si la fecha no es posterior a hoy, o si el evento no tiene
     *                                     lugar o el lugar no tiene sectores
     * @throws SectoresSinPrecioException  si algún sector no tiene precio asignado
     * @throws RemoteServiceException      si ms-tickets no está disponible o responde con error
     */
    @Transactional
    public EventoResponseDTO publicarEvento(Long idEvento, Long organizadorId, boolean esAdmin) {
        Evento evento = eventoRepository.findById(idEvento)
                .orElseThrow(() -> new NotFoundException("Evento " + idEvento + " no existe"));

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés publicar un evento que no te pertenece");
        }

        EstadoEvento estadoPublicado = estadoPublicado();
        if (evento.getEstado() == estadoPublicado) {
            return toResponseDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden publicar eventos en estado BORRADOR");
        }

        if (evento.getFecha() == null || !evento.getFecha().isAfter(LocalDate.now())) {
            throw new BadRequestException("La fecha del evento debe ser posterior a hoy");
        }

        if (evento.getLugar() == null) {
            throw new BadRequestException("El evento no tiene un lugar asociado");
        }

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

        if (!faltantes.isEmpty()) {
            throw new SectoresSinPrecioException(faltantes);
        }

        try {
            ticketsClient.crearDisponibilidad(evento.getId(), buildInventarioRequest(evento, sectores));

        }  catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");

        } catch (FeignException e) {
            throw new RemoteServiceException(e.status(), "ms-tickets respondió con error");
        }

        evento.setEstado(estadoPublicado);
        Evento guardado = eventoRepository.save(evento);
        return toResponseDTO(guardado);
    }

    /**
     * Cancela un evento publicado. Solo puede ser realizado por un administrador.
     * <p>
     * Valida que el usuario sea administrador, que el evento exista y que esté en estado
     * PUBLICADO. Si las validaciones pasan, avisa a ms-tickets para que bloquee nuevas
     * reservas y compras, y recién después cambia el estado a CANCELADO.
     * <p>
     * Es idempotente: si el evento ya estaba cancelado, devuelve su información
     * sin volver a modificarlo ni a llamar a ms-tickets.
     *
     * @param id      id del evento a cancelar
     * @param esAdmin {@code true} si el usuario es administrador
     *
     * @return DTO con la información del evento cancelado
     *
     * @throws ForbiddenException     si el usuario no es administrador
     * @throws NotFoundException      si el evento no existe
     * @throws ConflictException      si el evento no está en estado PUBLICADO ni CANCELADO
     * @throws RemoteServiceException si ms-tickets no está disponible o responde con error
     */
    @Transactional
    public EventoResponseDTO cancelarEvento(Long id, boolean esAdmin) {

        if (!esAdmin) {
            throw new ForbiddenException("Solo administradores pueden cancelar un evento");
        }

        Evento evento = eventoRepository.findById(id).orElseThrow(
                () -> {
                   return new NotFoundException("No se encontró el evento solicitado");
                });

        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            return toResponseDTO(evento);
        }

        if(evento.getEstado() != EstadoEvento.PUBLICADO){
            throw new ConflictException("El evento debe ser publicado para poder ser cancelado");
        }

        evento.setEstado(EstadoEvento.CANCELADO);
        evento = eventoRepository.save(evento);

        try {
            ticketsClient.cancelarEvento(evento.getId());

        }  catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");

        } catch (FeignException e) {
            throw new RemoteServiceException(e.status(), "ms-tickets respondió con error");
        }

        //TO DO : REINTEGRO MASIVO

        return toResponseDTO(evento);

    }


    /***
     * Metodo auxiliar.
     * PUBLICADO actualmente no esta soportado en el enum, es para que compile. A eliminar en
     * proximos task.
     *
     */
    private EstadoEvento estadoPublicado() {
        try {
            return EstadoEvento.valueOf("PUBLICADO");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "El enum EstadoEvento no tiene PUBLICADO", e);
        }
    }

    /**
     * Arma el request de inventario para ms-tickets a partir de los sectores del evento.
     * Los sectores se ordenan por id para que el payload sea determinista.
     *
     * @param evento   evento que se está publicando
     * @param sectores sectores del lugar del evento
     *
     * @return request listo para enviar a ms-tickets
     */
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

    /**
     * Convierte un Sector en el request que describe su inventario.
     * Si es numerado, incluye los asientos ordenados por fila y número;
     * si no es numerado, incluye la capacidad máxima.
     *
     * @param sector sector a convertir
     *
     * @return request con el inventario del sector
     *
     * @throws IllegalStateException si el tipo de sector no está soportado
     */
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



    /**
     * Convierte un Evento en el DTO de respuesta con su id, nombre y estado.
     *
     * @param evento evento a convertir
     *
     * @return DTO con la información básica del evento
     */
    private EventoResponseDTO toResponseDTO(Evento evento) {
        return EventoResponseDTO.builder()
                .id(evento.getId())
                .nombre(evento.getNombre())
                .estado(evento.getEstado().name())
                .build();
    }
}
