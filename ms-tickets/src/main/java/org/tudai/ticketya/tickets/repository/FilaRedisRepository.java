package org.tudai.ticketya.tickets.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la fila virtual de un evento en Redis.
 * <p>
 * La fila es un sorted set cuya clave es {@code tickets:fila:evento:{eventoId}} y
 * cuyo miembro es el id del cliente. El alta y la consulta de la posición se hacen
 * en un único script Lua atómico:
 * <ul>
 *     <li>Si el cliente ya existe, no se modifica su score ni su posición.</li>
 *     <li>Si es nuevo, el score es el momento de llegada según el reloj de Redis
 *     en microsegundos, no el reloj local de la instancia.</li>
 * </ul>
 * Usar el reloj de Redis evita que dos instancias de {@code ms-tickets} con relojes
 * desincronizados inviertan el orden de llegada.
 */
@Repository
@RequiredArgsConstructor
public class FilaRedisRepository {

    public static final String PREFIJO_CLAVE = "tickets:fila:evento:";

    private static final RedisScript<List> REGISTRAR = new DefaultRedisScript<>("""
            local actual = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if actual then
                local score = math.floor(tonumber(actual))
                -- Compatibilidad con scores en milisegundos de versiones anteriores.
                if score < 100000000000000 then
                    score = score * 1000
                end
                return {0, redis.call('ZRANK', KEYS[1], ARGV[1]),
                        redis.call('ZCARD', KEYS[1]), score}
            end
            local ahora = redis.call('TIME')
            local score = tonumber(ahora[1]) * 1000000 + tonumber(ahora[2])
            redis.call('ZADD', KEYS[1], score, ARGV[1])
            return {1, redis.call('ZRANK', KEYS[1], ARGV[1]),
                    redis.call('ZCARD', KEYS[1]), score}
            """, List.class);

    private static final RedisScript<List> CONSULTAR = new DefaultRedisScript<>("""
            local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
            if not rank then
                return {}
            end
            return {rank, redis.call('ZCARD', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public Registro registrar(Long eventoId, Long clienteId) {
        List<?> resultado = redisTemplate.execute(
                REGISTRAR, List.of(clave(eventoId)), String.valueOf(clienteId));

        if (resultado == null || resultado.size() < 4) {
            throw new IllegalStateException(
                    "No se pudo obtener el turno del cliente " + clienteId
                            + " para el evento " + eventoId);
        }

        long scoreMicros = numero(resultado.get(3));
        return new Registro(
                numero(resultado.get(0)) == 1L,
                numero(resultado.get(1)) + 1,
                numero(resultado.get(2)),
                Instant.ofEpochSecond(
                        scoreMicros / 1_000_000,
                        (scoreMicros % 1_000_000) * 1_000));
    }

    public Optional<Posicion> consultar(Long eventoId, Long clienteId) {
        List<?> resultado = redisTemplate.execute(
                CONSULTAR, List.of(clave(eventoId)), String.valueOf(clienteId));

        if (resultado == null || resultado.isEmpty()) {
            return Optional.empty();
        }
        if (resultado.size() < 2) {
            throw new IllegalStateException(
                    "No se pudo obtener la posición del cliente " + clienteId
                            + " para el evento " + eventoId);
        }

        long posicion = numero(resultado.get(0));
        return Optional.of(new Posicion(
                posicion + 1, posicion, numero(resultado.get(1))));
    }

    public static String clave(Long eventoId) {
        return PREFIJO_CLAVE + eventoId;
    }

    private static long numero(Object valor) {
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        return Long.parseLong(String.valueOf(valor));
    }

    public record Registro(boolean agregado, long posicion, long totalEnFila,
                           Instant ingresadoEl) {
    }

    public record Posicion(long posicion, long personasDelante, long totalEnFila) {
    }
}
