package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.config.HabilitacionProperties;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.HabilitacionEvento;
import org.tudai.ticketya.tickets.dto.HabilitacionRequestDTO;
import org.tudai.ticketya.tickets.repository.HabilitacionEventoRepository;
import org.tudai.ticketya.tickets.repository.PaseRedisRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la habilitacion de clientes por tandas y la validacion de pases.
 * <p>
 * La coordinacion entre instancias se hace en la base: {@link HabilitacionEventoRepository#reclamar}
 * actualiza {@code proxima_tanda_el} de forma atomica y solo una instancia gana la tanda.
 * La emision de los pases y la extraccion de la fila se hacen en un unico script Lua.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HabilitacionTandaService {

    private final HabilitacionEventoRepository habilitacionRepository;
    private final PaseRedisRepository paseRepository;
    private final HabilitacionProperties properties;

    public void ejecutarTandas() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant ahora = ahoraDeBase();
        try {
            habilitacionRepository.insertarFaltantes(
                    tamanoPorDefecto(),
                    intervaloPorDefecto(),
                    ttlPorDefecto(),
                    ahora);
        } catch (RuntimeException e) {
            // Otra instancia pudo crear la configuracion al mismo tiempo.
            log.warn("No se pudo completar la configuracion de habilitacion faltante", e);
        }

        for (Long eventoId : habilitacionRepository.buscarVencidos(ahora)) {
            HabilitacionEvento config = habilitacionRepository.findById(eventoId).orElse(null);
            if (config == null || !config.isHabilitada()) {
                continue;
            }

            int tamano = config.getTamanoTanda() > 0
                    ? config.getTamanoTanda() : tamanoPorDefecto();
            int intervalo = config.getIntervaloSegundos() > 0
                    ? config.getIntervaloSegundos() : intervaloPorDefecto();
            int ttl = config.getTtlPaseSegundos() > 0
                    ? config.getTtlPaseSegundos() : ttlPorDefecto();

            Instant proxima = ahora.plusSeconds(intervalo);
            if (habilitacionRepository.reclamar(eventoId, ahora, proxima) == 0) {
                continue;
            }

            try {
                List<PaseRedisRepository.Pase> pases = paseRepository.habilitarSiguientes(
                        eventoId, tamano, Duration.ofSeconds(ttl), UUID.randomUUID().toString());

                if (!pases.isEmpty()) {
                    try {
                        habilitacionRepository.incrementarHabilitados(eventoId, pases.size());
                    } catch (RuntimeException e) {
                        log.warn("No se pudo actualizar el total habilitado del evento {}",
                                eventoId, e);
                    }
                    log.info("Evento {}: {} clientes habilitados en la tanda",
                            eventoId, pases.size());
                }
            } catch (RuntimeException e) {
                habilitacionRepository.reprogramar(eventoId, ahora);
                log.error("No se pudo emitir la tanda del evento {}", eventoId, e);
            }
        }
    }

    /**
     * Indica si el evento exige un pase para reservar. Solo aplica a eventos
     * de alta demanda con la habilitacion por tandas activa.
     */
    public boolean requierePase(Long eventoId, DisponibilidadEvento evento) {
        if (!properties.isEnabled() || !evento.isAltaDemanda()) {
            return false;
        }
        return habilitacionRepository.findById(eventoId)
                .map(HabilitacionEvento::isHabilitada)
                .orElse(false);
    }

    public void validarPase(Long eventoId, Long clienteId, String token) {
        if (!paseRepository.validar(eventoId, clienteId, token)) {
            throw new ConflictException(
                    "El cliente " + clienteId + " no esta habilitado para comprar en el evento "
                            + eventoId + " o su pase vencio");
        }
    }

    /**
     * Crea, reactiva o deshabilita la configuracion de habilitacion cuando se
     * publica un evento o cambia su condicion de alta demanda.
     */
    public void sincronizar(Long eventoId, boolean altaDemanda) {
        if (altaDemanda) {
            habilitacionRepository.insertarSiNoExiste(
                    eventoId,
                    tamanoPorDefecto(),
                    intervaloPorDefecto(),
                    ttlPorDefecto(),
                    Instant.now());
            // Si el evento ya existia y estaba pausado, vuelve a habilitarse.
            habilitacionRepository.reactivar(eventoId, Instant.now());
        } else {
            habilitacionRepository.deshabilitar(eventoId);
        }
    }

    public void reactivar(Long eventoId) {
        habilitacionRepository.reactivar(eventoId, Instant.now());
    }

    @Transactional
    public void configurar(Long eventoId, HabilitacionRequestDTO request) {
        if (request == null) {
            throw new BadRequestException("La configuracion de habilitacion es obligatoria");
        }

        HabilitacionEvento config = habilitacionRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException(
                        "No hay configuracion de habilitacion para el evento " + eventoId));

        if (request.getHabilitada() != null) {
            config.setHabilitada(request.getHabilitada());
        }
        if (request.getTamanoTanda() != null) {
            if (request.getTamanoTanda() < 1) {
                throw new BadRequestException("El tamaño de tanda debe ser mayor a 0");
            }
            config.setTamanoTanda(request.getTamanoTanda());
        }
        if (request.getIntervaloSegundos() != null) {
            if (request.getIntervaloSegundos() < 1) {
                throw new BadRequestException("El intervalo debe ser mayor a 0 segundos");
            }
            config.setIntervaloSegundos(request.getIntervaloSegundos());
        }
        if (request.getTtlPaseSegundos() != null) {
            if (request.getTtlPaseSegundos() < 1) {
                throw new BadRequestException("El TTL del pase debe ser mayor a 0 segundos");
            }
            config.setTtlPaseSegundos(request.getTtlPaseSegundos());
        }
        if (config.getProximaTandaEl() == null) {
            config.setProximaTandaEl(Instant.now());
        }

        habilitacionRepository.save(config);
    }

    private Instant ahoraDeBase() {
        Object ahora = habilitacionRepository.ahora();
        if (ahora instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (ahora instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (ahora instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (ahora instanceof Instant instant) {
            return instant;
        }
        return Instant.now();
    }

    private int tamanoPorDefecto() {
        return properties.getTamanoTanda() > 0 ? properties.getTamanoTanda() : 50;
    }

    private int intervaloPorDefecto() {
        return segundos(properties.getIntervalo(), 30);
    }

    private int ttlPorDefecto() {
        return segundos(properties.getTtlPase(), 300);
    }

    private int segundos(Duration duracion, int defecto) {
        if (duracion == null) {
            return defecto;
        }
        long segundos = duracion.toSeconds();
        if (segundos < 1) {
            return defecto;
        }
        return (int) Math.min(segundos, Integer.MAX_VALUE);
    }
}
