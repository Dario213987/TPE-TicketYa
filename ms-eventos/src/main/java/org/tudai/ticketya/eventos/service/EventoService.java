package org.tudai.ticketya.eventos.service;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;
import org.tudai.ticketya.eventos.dto.evento.EventoPublicadoDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoUpdateDTO;
import org.tudai.ticketya.eventos.dto.evento.PrecioEventoSectorResponseDTO;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventoService {

    private static final Set<EstadoEvento> ESTADOS_FINALIZABLES = EnumSet.of(
            EstadoEvento.PUBLICADO,
            EstadoEvento.AGOTADO,
            EstadoEvento.SUSPENDIDO);

    private final EventoRepository eventoRepository;
    private final TicketsClient ticketsClient;
    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;
    private final NotificacionTicketsService notificacionTicketsService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(Pageable pageable) {
        return eventoRepository.findByEstadoIn(EstadoEvento.VISIBLES, pageable);
    }


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
    public EventoPublicadoDTO publicarEvento(Long idEvento, Long organizadorId, boolean esAdmin) {
        PreparacionPublicacion preparacion = transactionTemplate.execute(status ->
                prepararPublicacion(idEvento, organizadorId, esAdmin));

        if (preparacion == null) {
            throw new IllegalStateException("No se pudo preparar la publicación del evento " + idEvento);
        }
        if (preparacion.yaPublicado() != null) {
            return preparacion.yaPublicado();
        }

        try {
            ticketsClient.crearDisponibilidad(preparacion.request().getEventoId(), preparacion.request());

        } catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");

        } catch (FeignException e) {
            throw new RemoteServiceException(e.status(), "ms-tickets respondió con error");
        }

        Evento guardado = transactionTemplate.execute(status -> confirmarPublicacion(idEvento));
        return toPublicadoDTO(guardado);
    }

    private PreparacionPublicacion prepararPublicacion(Long idEvento, Long organizadorId, boolean esAdmin) {
        Evento evento = eventoRepository.findById(idEvento)
                .orElseThrow(() -> new NotFoundException("Evento " + idEvento + " no existe"));

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés publicar un evento que no te pertenece");
        }

        if (evento.getEstado() == estadoPublicado()) {
            return new PreparacionPublicacion(null, toPublicadoDTO(evento));
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

        return new PreparacionPublicacion(buildInventarioRequest(evento, sectores), null);
    }

    private Evento confirmarPublicacion(Long idEvento) {
        Evento evento = eventoRepository.findById(idEvento)
                .orElseThrow(() -> new NotFoundException("Evento " + idEvento + " no existe"));

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("El evento cambió de estado durante la publicación");
        }

        evento.setEstado(estadoPublicado());
        return eventoRepository.save(evento);
    }

    private record PreparacionPublicacion(PublicarEventoRequest request, EventoPublicadoDTO yaPublicado) {
    }

    /**
     * Cancela un evento publicado. Solo puede ser realizado por un administrador.
     * <p>
     * Valida que el usuario sea administrador, que el evento exista y que esté en estado
     * PUBLICADO o SUSPENDIDO. Si las validaciones pasan, avisa a ms-tickets para que bloquee
     * nuevas reservas y compras, y recién después cambia el estado a CANCELADO.
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
     * @throws ConflictException      si el evento no está en estado PUBLICADO, SUSPENDIDO ni CANCELADO
     * @throws RemoteServiceException si ms-tickets no está disponible o responde con error
     */
    public EventoPublicadoDTO cancelarEvento(Long id, boolean esAdmin) {

        if (!esAdmin) {
            throw new ForbiddenException("Solo administradores pueden cancelar un evento");
        }

        EventoPublicadoDTO yaCancelado = transactionTemplate.execute(status -> validarCancelacion(id));
        if (yaCancelado != null) {
            return yaCancelado;
        }

        try {
            ticketsClient.cancelarEvento(id);

        }  catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");

        } catch (FeignException e) {
            throw new RemoteServiceException(e.status(), "ms-tickets respondió con error");
        }

        //TO DO : REINTEGRO MASIVO

        Evento guardado = transactionTemplate.execute(status -> confirmarCancelacion(id));
        return toPublicadoDTO(guardado);

    }

    private EventoPublicadoDTO validarCancelacion(Long id) {
        Evento evento = eventoRepository.findById(id).orElseThrow(
                () -> new NotFoundException("No se encontró el evento solicitado"));

        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            return toPublicadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO
                && evento.getEstado() != EstadoEvento.SUSPENDIDO) {
            throw new ConflictException("El evento debe ser publicado para poder ser cancelado");
        }

        return null;
    }

    private Evento confirmarCancelacion(Long id) {
        Evento evento = eventoRepository.findById(id).orElseThrow(
                () -> new NotFoundException("No se encontró el evento solicitado"));

        if (evento.getEstado() != EstadoEvento.PUBLICADO
                && evento.getEstado() != EstadoEvento.SUSPENDIDO) {
            throw new ConflictException("El evento cambió de estado durante la cancelación");
        }

        evento.setEstado(EstadoEvento.CANCELADO);
        return eventoRepository.save(evento);
    }

    /**
     * Finaliza los eventos cuya fecha ya pasó.
     * <p>
     * Pasa a FINALIZADO los eventos con fecha anterior a hoy que estén en estado
     * PUBLICADO, AGOTADO o SUSPENDIDO. Un evento se finaliza al día siguiente de su
     * fecha, ya que la comparación es estrictamente anterior a hoy.
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
        LocalDate hoy = LocalDate.now();
        List<Long> ids = eventoRepository.findIdsEventosVencidos(hoy, ESTADOS_FINALIZABLES);

        if (ids.isEmpty()) {
            return;
        }

        eventoRepository.finalizarEventosVencidos(hoy, EstadoEvento.FINALIZADO, ESTADOS_FINALIZABLES);

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
    public EventoResponseDTO marcarAgotado(Long id){

        Evento evento = buscarEvento(id);

        if(estaEn(evento, EstadoEvento.AGOTADO)){
            return toResponseDTO(evento);
        }

        if (!estaEn(evento, EstadoEvento.PUBLICADO)) {
            throw new ConflictException("Solo un evento publicado puede pasar a agotado");
        }


        evento.setEstado(EstadoEvento.AGOTADO);
        evento = eventoRepository.save(evento);

        return toResponseDTO(evento);
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
    public EventoResponseDTO marcarPublicado(Long id){

        Evento evento = buscarEvento(id);

        if(estaEn(evento, EstadoEvento.PUBLICADO)){
            return toResponseDTO(evento);
        }

        if (!estaEn(evento, EstadoEvento.AGOTADO)) {
            throw new ConflictException("Solo un evento agotado puede pasar a publicado");
        }

        evento.setEstado(EstadoEvento.PUBLICADO);
        evento = eventoRepository.save(evento);

        return toResponseDTO(evento);
    }

    public EventoPublicadoDTO suspenderEvento(Long id, boolean esAdmin) {

        if (!esAdmin) {
            throw new ForbiddenException("Solo administradores pueden suspender un evento");
        }

        EventoPublicadoDTO yaSuspendido = transactionTemplate.execute(status -> validarSuspension(id));
        if (yaSuspendido != null) {
            return yaSuspendido;
        }

        try {
            ticketsClient.suspenderEvento(id);

        } catch (RetryableException e) {
            throw new RemoteServiceException(502, "ms-tickets no disponible");

        } catch (FeignException e) {
            throw new RemoteServiceException(e.status(), "ms-tickets respondió con error");
        }

        Evento guardado = transactionTemplate.execute(status -> confirmarSuspension(id));
        return toPublicadoDTO(guardado);
    }

    private EventoPublicadoDTO validarSuspension(Long id) {
        Evento evento = eventoRepository.findById(id).orElseThrow(
                () -> new NotFoundException("No se encontró el evento solicitado"));

        if (evento.getEstado() == EstadoEvento.SUSPENDIDO) {
            return toPublicadoDTO(evento);
        }

        if (evento.getEstado() != EstadoEvento.PUBLICADO) {
            throw new ConflictException("El evento debe ser publicado para poder ser suspendido");
        }

        return null;
    }

    private Evento confirmarSuspension(Long id) {
        Evento evento = eventoRepository.findById(id).orElseThrow(
                () -> new NotFoundException("No se encontró el evento solicitado"));

        if (evento.getEstado() != EstadoEvento.PUBLICADO) {
            throw new ConflictException("El evento cambió de estado durante la suspensión");
        }

        evento.setEstado(EstadoEvento.SUSPENDIDO);
        return eventoRepository.save(evento);
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

    /**
     * Verifica si un evento se encuentra en un estado determinado.
     *
     * @param evento evento a analizar
     * @param estado estado contra el que se compara
     *
     * @return {@code true} si el estado actual del evento es el indicado
     */
    private boolean estaEn(Evento evento, EstadoEvento estado) {
        return evento.getEstado() == estado;
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
    private EventoPublicadoDTO toPublicadoDTO(Evento evento) {
        return EventoPublicadoDTO.builder()
                .id(evento.getId())
                .nombre(evento.getNombre())
                .estado(evento.getEstado().name())
                .build();
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
                .estado(evento.getEstado())
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
