package org.tudai.ticketya.tickets.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Acceso a los pases de compra de la fila virtual.
 * <p>
 * Una tanda toma a los primeros N clientes del sorted set de la fila y les crea
 * una clave por cliente con TTL. Todo se hace en un unico script Lua: el
 * {@code ZPOPMIN} y la emision de los pases son atomicos, por lo que dos
 * instancias nunca habilitan al mismo cliente ni saltean a nadie.
 */
@Repository
@RequiredArgsConstructor
public class PaseRedisRepository {

    public static final String PREFIJO_PASE = "tickets:pase:evento:";

    private static final RedisScript<List> HABILITAR = new DefaultRedisScript<>("""
            local cola = KEYS[1]
            local prefijoPase = ARGV[1]
            local cantidad = tonumber(ARGV[2])
            local ttlMs = tonumber(ARGV[3])
            local tanda = ARGV[4]

            local ahora = redis.call('TIME')
            local ahoraMs = tonumber(ahora[1]) * 1000 + math.floor(tonumber(ahora[2]) / 1000)
            local expiraEl = ahoraMs + ttlMs

            local elementos = redis.call('ZPOPMIN', cola, cantidad)
            local pases = {}

            for i = 1, #elementos, 2 do
                local cliente = elementos[i]
                local score = elementos[i + 1]
                local clavePase = prefijoPase .. cliente
                local token = tanda .. ':' .. cliente

                redis.call('HSET', clavePase,
                    'clienteId', cliente,
                    'token', token,
                    'tanda', tanda,
                    'score', score,
                    'emitidoEl', ahoraMs,
                    'expiraEl', expiraEl)
                redis.call('PEXPIREAT', clavePase, expiraEl)

                table.insert(pases, cliente)
                table.insert(pases, token)
                table.insert(pases, score)
                table.insert(pases, ahoraMs)
                table.insert(pases, expiraEl)
            end

            return pases
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * Habilita a los primeros {@code cantidad} clientes de la fila.
     *
     * @return los pases emitidos; vacio si la fila no tiene clientes
     */
    public List<Pase> habilitarSiguientes(Long eventoId, int cantidad, Duration ttl,
                                          String tandaId) {
        if (cantidad < 1) {
            throw new IllegalArgumentException("La cantidad de la tanda debe ser mayor a 0");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("El TTL del pase debe ser mayor a 0");
        }
        if (tandaId == null || tandaId.isBlank()) {
            throw new IllegalArgumentException("El id de la tanda es obligatorio");
        }

        List<?> resultado = redisTemplate.execute(
                HABILITAR,
                List.of(FilaRedisRepository.clave(eventoId)),
                prefijo(eventoId),
                String.valueOf(cantidad),
                String.valueOf(ttl.toMillis()),
                tandaId);

        if (resultado == null || resultado.isEmpty()) {
            return List.of();
        }
        if (resultado.size() % 5 != 0) {
            throw new IllegalStateException(
                    "Redis devolvio un resultado invalido al habilitar la tanda del evento "
                            + eventoId);
        }

        List<Pase> pases = new ArrayList<>(resultado.size() / 5);
        for (int i = 0; i < resultado.size(); i += 5) {
            String cliente = String.valueOf(resultado.get(i));
            pases.add(new Pase(
                    Long.parseLong(cliente),
                    String.valueOf(resultado.get(i + 1)),
                    tandaId,
                    numero(resultado.get(i + 2)),
                    Instant.ofEpochMilli(numero(resultado.get(i + 3))),
                    Instant.ofEpochMilli(numero(resultado.get(i + 4)))));
        }
        return pases;
    }

    public Optional<Pase> buscar(Long eventoId, Long clienteId) {
        Map<Object, Object> datos = redisTemplate.opsForHash()
                .entries(clave(eventoId, clienteId));

        if (datos == null || datos.isEmpty()) {
            return Optional.empty();
        }

        long expiraEl = numero(datos.get("expiraEl"));
        if (expiraEl <= 0) {
            Long ttl = redisTemplate.getExpire(clave(eventoId, clienteId), TimeUnit.MILLISECONDS);
            expiraEl = System.currentTimeMillis() + (ttl == null ? 0 : ttl);
        }

        long emitidoEl = numero(datos.get("emitidoEl"));
        if (emitidoEl <= 0) {
            emitidoEl = expiraEl;
        }

        return Optional.of(new Pase(
                clienteId,
                texto(datos.get("token")),
                texto(datos.get("tanda")),
                numero(datos.get("score")),
                Instant.ofEpochMilli(emitidoEl),
                Instant.ofEpochMilli(expiraEl)));
    }

    public boolean existe(Long eventoId, Long clienteId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(clave(eventoId, clienteId)));
    }

    public boolean validar(Long eventoId, Long clienteId, String token) {
        return buscar(eventoId, clienteId)
                .filter(pase -> token == null || token.isBlank()
                        || token.equals(pase.token()))
                .isPresent();
    }

    public static String clave(Long eventoId, Long clienteId) {
        return prefijo(eventoId) + clienteId;
    }

    public static String prefijo(Long eventoId) {
        return PREFIJO_PASE + eventoId + ":cliente:";
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static long numero(Object valor) {
        if (valor == null) {
            return 0;
        }
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(valor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public record Pase(Long clienteId, String token, String tanda, long score,
                       Instant emitidoEl, Instant expiraEl) {
    }
}
