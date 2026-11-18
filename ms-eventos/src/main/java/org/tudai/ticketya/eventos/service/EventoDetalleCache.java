package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.eventos.model.EventoDetalleResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.repository.EventoRepository;

/**
 * Cache del detalle de un evento ({@code GET /api/eventos/{id}}) en Redis (TKY-82).
 * <p>
 * Guarda el DTO de respuesta (nunca la entidad, que tiene relaciones lazy) con clave
 * {@code eventoId} en la cache {@value #CACHE}, con el TTL de
 * {@code ticketya.cache.ttl.evento-detalle}. Es un bean aparte de {@link EventoService} porque
 * {@code @Cacheable} no se aplica cuando un método llama a otro de la misma clase.
 * <p>
 * La regla de visibilidad de los borradores no se cachea: la aplica
 * {@link EventoService#obtenerEvento} sobre el detalle que devuelve este bean. Las operaciones de
 * {@link EventoService} que modifican un evento invalidan su entrada.
 */
@Component
@RequiredArgsConstructor
public class EventoDetalleCache {

    public static final String CACHE = "evento-detalle";

    private final EventoRepository eventoRepository;
    private final ModelMapper modelMapper;

    /**
     * Detalle del evento, desde la cache o la base. No aplica la regla de borradores.
     *
     * @throws NotFoundException si el evento no existe (no se cachea)
     */
    @Cacheable(cacheNames = CACHE, key = "#eventoId")
    @Transactional(readOnly = true)
    public EventoDetalleResponse obtener(Long eventoId) {
        return eventoRepository.findById(eventoId)
                .map(evento -> modelMapper.map(modelMapper.map(evento, EventoResponse.class),
                        EventoDetalleResponse.class))
                .orElseThrow(() -> new NotFoundException("Evento no encontrado con id: " + eventoId));
    }

    /** Invalida el detalle de un evento (después del commit si hay una transacción en curso). */
    @CacheEvict(cacheNames = CACHE, key = "#eventoId")
    public void invalidar(Long eventoId) {
        // La invalidación la hace @CacheEvict.
    }
}
