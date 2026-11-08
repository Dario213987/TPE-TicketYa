package org.tudai.ticketya.eventos.service;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoBorradorCreateRequest;
import org.tudai.ticketya.contract.eventos.model.EventoEstadoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoUpdateRequest;
import org.tudai.ticketya.contract.eventos.model.ParticipanteRequest;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorResponse;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.client.dto.AsientoRequest;
import org.tudai.ticketya.eventos.client.dto.DisponibilidadEventoResponse;
import org.tudai.ticketya.eventos.client.dto.PublicarEventoRequest;
import org.tudai.ticketya.eventos.client.dto.SectorDisponibilidadRequest;
import org.tudai.ticketya.eventos.client.dto.SectorDisponibilidadResponse;
import org.tudai.ticketya.eventos.client.dto.TipoSectorRequest;
import org.tudai.ticketya.eventos.domain.Asiento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;
import org.tudai.ticketya.eventos.exception.FechaEventoInvalidaException;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class EventoService {

    static final Set<EstadoEvento> ESTADOS_VISIBLES = Set.of(
            EstadoEvento.PUBLICADO, EstadoEvento.FINALIZADO, EstadoEvento.AGOTADO);

    private static final Set<EstadoEvento> ESTADOS_FINALIZABLES = EnumSet.of(
            EstadoEvento.PUBLICADO,
            EstadoEvento.AGOTADO,
            EstadoEvento.SUSPENDIDO);

    private final EventoRepository eventoRepository;
    private final TicketsClient ticketsClient;
    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;
    private final LugarService lugarService;
    private final ParticipanteService participanteService;
    private final NotificacionTicketsService notificacionTicketsService;
    private final TransactionTemplate transactionTemplate;

    @Value("${ticketya.eventos.finalizacion.horas-gracia:6}")
    private long horasGraciaFinalizacion = 6;


    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(Pageable pageable) {
        return eventoRepository.findByEstadoIn(ESTADOS_VISIBLES, pageable);
    }


    /**
     * Publica un evento que está en estado BORRADOR.
     * <p>
     * Valida que el evento exista, que el usuario tenga permiso (organizador dueño o administrador),
     * que la fecha/hora sea posterior al momento en que se solicita el cambio,
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
     * @throws BadRequestException         si la fecha/hora no es posterior al momento actual, o si el
     *                                     evento no tiene lugar o el lugar no tiene sectores
     * @throws SectoresSinPrecioException  si algún sector no tiene precio asignado
     * @throws RemoteServiceException      si ms-tickets no está disponible o responde con error
     */
    public EventoEstadoResponse publicarEvento(Long idEvento, Long organizadorId, boolean esAdmin) {
        PublicacionPendiente pendiente = transactionTemplate.execute(
                status -> prepararPublicacion(idEvento, organizadorId, esAdmin));

        if (pendiente.yaPublicado() != null) {
            return pendiente.yaPublicado();
        }

        ejecutarTickets(() -> ticketsClient.crearDisponibilidad(idEvento, pendiente.request()));

        return transactionTemplate.execute(status -> confirmarPublicacion(idEvento));
    }

    /**
     * Cancela un evento publicado. La autorización de administrador se valida en el controller.
     * <p>
     * Valida que el evento exista y que esté en estado PUBLICADO, SUSPENDIDO o AGOTADO. Si las
     * validaciones pasan, avisa a ms-tickets para que bloquee nuevas reservas y compras, y recién
     * después cambia el estado a CANCELADO.
     * <p>
     * Es idempotente: si el evento ya estaba cancelado, devuelve su información
     * sin volver a modificarlo ni a llamar a ms-tickets.
     *
     * @param id id del evento a cancelar
     *
     * @return DTO con la información del evento cancelado
     *
     * @throws NotFoundException      si el evento no existe
     * @throws ConflictException      si el evento no está en estado PUBLICADO, SUSPENDIDO, AGOTADO ni CANCELADO
     * @throws RemoteServiceException si ms-tickets no está disponible o responde con error
     */
    public EventoEstadoResponse cancelarEvento(Long id) {
        EventoEstadoResponse yaCancelado = transactionTemplate.execute(
                status -> validarCancelacion(id));

        if (yaCancelado != null) {
            return yaCancelado;
        }

        ejecutarTickets(() -> ticketsClient.cancelarEvento(id));

        return transactionTemplate.execute(status -> confirmarCancelacion(id));
    }

    public EventoEstadoResponse suspenderEvento(Long id) {
        EventoEstadoResponse yaSuspendido = transactionTemplate.execute(
                status -> validarSuspension(id));

        if (yaSuspendido != null) {
            return yaSuspendido;
        }

        ejecutarTickets(() -> ticketsClient.suspenderEvento(id));

        return transactionTemplate.execute(status -> confirmarSuspension(id));
    }

    public EventoEstadoResponse reactivarEvento(Long id) {
        EventoEstadoResponse yaReactivado = transactionTemplate.execute(
                status -> validarReactivacion(id));

        if (yaReactivado != null) {
            return yaReactivado;
        }

        EstadoEvento estadoDestino = estadoSegunDisponibilidad(id);

        ejecutarTickets(() -> ticketsClient.reactivarEvento(id));

        return transactionTemplate.execute(status -> confirmarReactivacion(id, estadoDestino));
    }

    private EstadoEvento estadoSegunDisponibilidad(Long id) {
        DisponibilidadEventoResponse disponibilidad = ejecutarTicketsConResultado(
                () -> ticketsClient.consultarDisponibilidad(id));

        if (disponibilidad == null || disponibilidad.getSectores() == null
                || disponibilidad.getSectores().isEmpty()) {
            return EstadoEvento.AGOTADO;
        }

        boolean hayDisponibles = disponibilidad.getSectores().stream()
                .map(SectorDisponibilidadResponse::getDisponibles)
                .anyMatch(disponibles -> disponibles != null && disponibles > 0);

        return hayDisponibles ? EstadoEvento.PUBLICADO : EstadoEvento.AGOTADO;
    }

    private PublicacionPendiente prepararPublicacion(Long idEvento, Long organizadorId, boolean esAdmin) {
        Evento evento = eventoRepository.findById(idEvento)
                .orElseThrow(() -> new NotFoundException("Evento " + idEvento + " no existe"));

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés publicar un evento que no te pertenece");
        }

        if (evento.getEstado() == EstadoEvento.PUBLICADO) {
            return new PublicacionPendiente(toEstadoDTO(evento), null);
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden publicar eventos en estado BORRADOR");
        }

        if (evento.getFecha() == null || !evento.getFecha().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("La fecha del evento debe ser posterior a hoy");
        }

        if (evento.getLugar() == null) {
            throw new BadRequestException("El evento no tiene un lugar asociado");
        }

        Set<Sector> sectores = evento.getLugar().getSectores();
        if (sectores == null || sectores.isEmpty()) {
            throw new BadRequestException("El lugar del evento no tiene sectores");
        }

        Map<Long, Double> preciosSectores = evento.getPreciosSectores();
        List<SectoresSinPrecioException.SectorFaltante> faltantes = sectores.stream()
                .filter(sector -> preciosSectores.get(sector.getId()) == null)
                .sorted(Comparator.comparing(Sector::getId))
                .map(sector -> new SectoresSinPrecioException.SectorFaltante(
                        sector.getId(), sector.getNombre()))
                .toList();

        if (!faltantes.isEmpty()) {
            throw new SectoresSinPrecioException(faltantes);
        }

        return new PublicacionPendiente(null, buildInventarioRequest(evento, sectores));
    }

    private EventoEstadoResponse confirmarPublicacion(Long idEvento) {
        Evento evento = buscarEventoParaCambioEstado(idEvento, "Evento " + idEvento + " no existe");

        if (evento.getEstado() == EstadoEvento.PUBLICADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden publicar eventos en estado BORRADOR");
        }

        evento.setEstado(EstadoEvento.PUBLICADO);
        return toEstadoDTO(eventoRepository.save(evento));
    }

    private EventoEstadoResponse validarCancelacion(Long id) {
        Evento evento = buscarEventoParaValidacion(id);

        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO
                && evento.getEstado() != EstadoEvento.SUSPENDIDO
                && evento.getEstado() != EstadoEvento.AGOTADO) {
            throw new ConflictException("El evento debe estar publicado, suspendido o agotado para poder ser cancelado");
        }

        return null;
    }

    private EventoEstadoResponse confirmarCancelacion(Long id) {
        Evento evento = buscarEventoParaCambioEstado(id);

        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO
                && evento.getEstado() != EstadoEvento.SUSPENDIDO
                && evento.getEstado() != EstadoEvento.AGOTADO) {
            throw new ConflictException("El evento debe estar publicado, suspendido o agotado para poder ser cancelado");
        }

        evento.setEstado(EstadoEvento.CANCELADO);

        //TO DO : REINTEGRO MASIVO

        return toEstadoDTO(eventoRepository.save(evento));
    }

    private EventoEstadoResponse validarSuspension(Long id) {
        Evento evento = buscarEventoParaValidacion(id);

        if (evento.getEstado() == EstadoEvento.SUSPENDIDO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO
                && evento.getEstado() != EstadoEvento.AGOTADO) {
            throw new ConflictException("El evento debe estar publicado o agotado para poder ser suspendido");
        }

        return null;
    }

    private EventoEstadoResponse confirmarSuspension(Long id) {
        Evento evento = buscarEventoParaCambioEstado(id);

        if (evento.getEstado() == EstadoEvento.SUSPENDIDO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO
                && evento.getEstado() != EstadoEvento.AGOTADO) {
            throw new ConflictException("El evento debe estar publicado o agotado para poder ser suspendido");
        }

        evento.setEstado(EstadoEvento.SUSPENDIDO);
        return toEstadoDTO(eventoRepository.save(evento));
    }

    private EventoEstadoResponse validarReactivacion(Long id) {
        Evento evento = buscarEventoParaValidacion(id);

        if (evento.getEstado() == EstadoEvento.PUBLICADO
                || evento.getEstado() == EstadoEvento.AGOTADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.SUSPENDIDO) {
            throw new ConflictException("El evento debe estar suspendido para poder ser reactivado");
        }

        return null;
    }

    private EventoEstadoResponse confirmarReactivacion(Long id, EstadoEvento estadoDestino) {
        Evento evento = buscarEventoParaCambioEstado(id);

        if (evento.getEstado() == EstadoEvento.PUBLICADO
                || evento.getEstado() == EstadoEvento.AGOTADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.SUSPENDIDO) {
            throw new ConflictException("El evento debe estar suspendido para poder ser reactivado");
        }

        evento.setEstado(estadoDestino);
        return toEstadoDTO(eventoRepository.save(evento));
    }

    /**
     * Finaliza los eventos cuya fecha/hora ya pasó hace más de
     * {@code ticketya.eventos.finalizacion.horas-gracia} horas (6 por defecto).
     * <p>
     * Pasa a FINALIZADO los eventos cuya fecha/hora sea estrictamente anterior al
     * momento actual menos las horas de gracia, siempre que estén en estado
     * PUBLICADO, AGOTADO o SUSPENDIDO.
     * <p>
     * El cambio de estado y el alta de la notificación pendiente se guardan en la
     * misma transacción. El envío a ms-tickets lo hace {@link NotificacionTicketsService}
     * después del commit, con reintentos, para no perder el aviso si ms-tickets no
     * está disponible.
     * <p>
     * Es idempotente: si se ejecuta dos veces seguidas, la segunda no modifica nada.
     */
    @Transactional
    public void finalizarEventosVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusHours(horasGraciaFinalizacion);
        List<Long> ids = eventoRepository.findIdsEventosVencidos(limite, ESTADOS_FINALIZABLES);

        if (ids.isEmpty()) {
            return;
        }

        eventoRepository.finalizarEventosVencidos(limite, EstadoEvento.FINALIZADO, ESTADOS_FINALIZABLES);

        for (Long id : ids) {
            notificacionTicketsService.encolar(id, TipoNotificacionTickets.FINALIZAR);
        }
    }

    /**
     * Marca un evento publicado como AGOTADO.
     * <p>
     * Valida que el evento exista y que esté en estado PUBLICADO. Si las validaciones pasan,
     * cambia el estado a AGOTADO.
     * <p>
     * Es idempotente: si el evento ya estaba agotado, devuelve su información
     * sin volver a modificarlo.
     *
     * @param id id del evento a marcar como agotado
     *
     * @return DTO con la información del evento agotado
     *
     * @throws NotFoundException si el evento no existe
     * @throws ConflictException si el evento no está en estado PUBLICADO ni AGOTADO
     */
    @Transactional
    public EventoEstadoResponse marcarAgotado(Long id) {
        Evento evento = buscarEventoParaCambioEstado(id, "Evento no encontrado con id: " + id);

        if (evento.getEstado() == EstadoEvento.AGOTADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO) {
            throw new ConflictException("Solo un evento publicado puede pasar a agotado");
        }

        evento.setEstado(EstadoEvento.AGOTADO);
        return toEstadoDTO(eventoRepository.save(evento));
    }

    /**
     * Vuelve a poner en venta un evento que estaba AGOTADO, pasándolo a PUBLICADO.
     * <p>
     * Valida que el evento exista y que esté en estado AGOTADO. No vuelve a crear el inventario
     * en ms-tickets, porque ya fue creado cuando el evento se publicó por primera vez.
     * <p>
     * Es idempotente: si el evento ya estaba publicado, devuelve su información
     * sin volver a modificarlo.
     *
     * @param id id del evento a reabrir
     *
     * @return DTO con la información del evento publicado
     *
     * @throws NotFoundException si el evento no existe
     * @throws ConflictException si el evento no está en estado AGOTADO ni PUBLICADO
     */
    @Transactional
    public EventoEstadoResponse marcarPublicado(Long id) {
        Evento evento = buscarEventoParaCambioEstado(id, "Evento no encontrado con id: " + id);

        if (evento.getEstado() == EstadoEvento.PUBLICADO) {
            return toEstadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.AGOTADO) {
            throw new ConflictException("Solo un evento agotado puede pasar a publicado");
        }

        evento.setEstado(EstadoEvento.PUBLICADO);
        return toEstadoDTO(eventoRepository.save(evento));
    }

    private Evento buscarEventoParaValidacion(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No se encontró el evento solicitado"));
    }

    private Evento buscarEventoParaCambioEstado(Long id) {
        return buscarEventoParaCambioEstado(id, "No se encontró el evento solicitado");
    }

    private Evento buscarEventoParaCambioEstado(Long id, String mensaje) {
        return eventoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(mensaje));
    }

    private void ejecutarTickets(Runnable llamada) {
        try {
            llamada.run();
        } catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");
        } catch (FeignException e) {
            throw new RemoteServiceException(502, "ms-tickets respondió con error");
        }
    }

    private <T> T ejecutarTicketsConResultado(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");
        } catch (FeignException e) {
            throw new RemoteServiceException(502, "ms-tickets respondió con error");
        }
    }

    @Transactional
    public EventoResponse actualizarEvento(Long eventoId, EventoUpdateRequest dto, Long organizadorId,
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

    private void validarCamposEstructurales(Evento evento, EventoUpdateRequest dto) {
        boolean esBorrador = evento.getEstado() == EstadoEvento.BORRADOR;

        if (dto.getFecha() != null && !Objects.equals(dto.getFecha(), evento.getFecha())) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se puede modificar la fecha de un evento ya publicado. Cancelalo o reprogramalo.");
            }
            if (!dto.getFecha().isAfter(LocalDateTime.now())) {
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

        if (dto.getSectores() != null
                && !Objects.equals(Set.copyOf(dto.getSectores()), sectoresDelEvento(evento))) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se pueden modificar los sectores de un evento ya publicado. Cancelalo o reprogramalo.");
            }
            throw new BadRequestException(
                    "Los sectores de un evento se administran desde el lugar asociado");
        }
    }

    private void aplicarCambios(Evento evento, EventoUpdateRequest dto) {
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
            evento.setTipo(dto.getTipo());
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

    private EventoResponse toResponse(Evento evento) {
        return modelMapper.map(evento, EventoResponse.class);
    }

    @Transactional
    public PrecioEventoSectorResponse definirPrecioSector(Long eventoId, Long sectorId, Double precio,
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

        return new PrecioEventoSectorResponse(eventoId, sectorId, precio);
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
                .map(sector -> toSectorDisponibilidadRequest(
                        sector, evento.getPreciosSectores().get(sector.getId())))
                .toList();

        return PublicarEventoRequest.builder()
                .eventoId(evento.getId())
                .organizadorId(evento.getOrganizadorId())
                .sectores(items)
                .build();
    }

    /**
     * Convierte un Sector en el request que describe su inventario.
     * Si es numerado, incluye los asientos ordenados por fila y número;
     * si no es numerado, incluye la capacidad máxima.
     *
     * @param sector sector a convertir
     * @param precio precio del sector para el evento
     *
     * @return request con el inventario del sector
     *
     * @throws IllegalStateException si el tipo de sector no está soportado
     */
    private SectorDisponibilidadRequest toSectorDisponibilidadRequest(Sector sector, Double precio) {
        SectorDisponibilidadRequest.SectorDisponibilidadRequestBuilder builder =
                SectorDisponibilidadRequest.builder().sectorId(sector.getId()).precio(precio);

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
    private EventoEstadoResponse toEstadoDTO(Evento evento) {
        return new EventoEstadoResponse(evento.getId(), evento.getNombre(), evento.getEstado());
    }

    private boolean sectorPerteneceAlLugar(Evento evento, Long sectorId) {
        Lugar lugar = evento.getLugar();
        if (lugar == null || lugar.getSectores() == null) {
            return false;
        }
        return lugar.getSectores().stream()
                .anyMatch(sector -> Objects.equals(sector.getId(), sectorId));
    }

    @Transactional
    public EventoResponse crearEventoBorrador(EventoBorradorCreateRequest request, Long organizadorId) {

        if (request.getFecha() == null || !request.getFecha().isAfter(LocalDateTime.now())) {
            throw new FechaEventoInvalidaException("La fecha del evento debe ser posterior a hoy");
        }
        if (request.getNombre() == null || request.getNombre().isBlank()
                || request.getDescripcion() == null || request.getDescripcion().isBlank()
                || request.getTipo() == null || request.getLugarId() == null) {
            throw new BadRequestException("Nombre, descripción, tipo, fecha y lugar son obligatorios");
        }

        Lugar lugar = lugarService.obtenerPorId(request.getLugarId());

        LocalDateTime fecha = request.getFecha();

        Evento evento = Evento.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .tipo(request.getTipo())
                .organizadorId(organizadorId)
                .lugar(lugar)
                .fecha(fecha)
                .estado(EstadoEvento.BORRADOR)
                .build();

        if (request.getParticipantes() != null && !request.getParticipantes().isEmpty()) {
            Set<Participante> participantesResueltos = request.getParticipantes().stream()
                    .map(this::resolverParticipante)
                    .collect(Collectors.toSet());
            evento.getParticipantes().addAll(participantesResueltos);
        }

        Evento guardado = eventoRepository.save(evento);
        return toResponse(guardado);
    }

    private Participante resolverParticipante(ParticipanteRequest request) {

        if (request == null || (request.getId() == null
                && (request.getNombre() == null || request.getNombre().isBlank()))) {
            throw new BadRequestException("Cada participante debe incluir un id o un nombre");
        }

        if (request.getId() != null) {
            return participanteService.obtenerPorId(request.getId());
        }

        return participanteService.crearParticipante(request);
    }

    private record PublicacionPendiente(EventoEstadoResponse yaPublicado, PublicarEventoRequest request) {
    }

}
