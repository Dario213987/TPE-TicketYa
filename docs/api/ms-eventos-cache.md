# Cache de ms-eventos (Redisson)

Capa de cache de `ms-eventos` sobre Redis con [Redisson](https://redisson.org/) (TKY-81). Qué se
cachea y cómo se invalida lo definen TKY-82 (detalle de evento) y TKY-83 (búsquedas); qué **no**
se cachea, TKY-84.

## Infraestructura

- Redis propio: `redis-eventos` en `infra/docker-compose.yml`, puerto `6382` (una instancia por
  servicio, como `redis-tickets` y `redis-preferencias`).
- Configuración en `CacheConfig` (`ms-eventos/.../config`): `@EnableCaching`, un `RedissonClient`
  y un `RedissonSpringCacheManager`. Se usa el núcleo de Redisson (`org.redisson:redisson`), no el
  starter de Spring Boot, que se conecta al arrancar.

## TTL por cache

Cada cache tiene su TTL en `ticketya.cache.ttl.<nombre>` (formato `Duration` de Spring):

```properties
ticketya.cache.ttl.evento-detalle=PT5M
ticketya.cache.ttl.eventos-busqueda=PT30S
```

Las caches que no figuran usan `ticketya.cache.ttl-por-defecto` (5 min): ninguna queda sin
vencimiento.

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `ticketya.cache.redis-host` | `EVENTOS_REDIS_HOST` | `localhost` |
| `ticketya.cache.redis-port` | `EVENTOS_REDIS_PORT` | `6382` |
| `ticketya.cache.connect-timeout` | `EVENTOS_CACHE_CONNECT_TIMEOUT` | `PT1S` |
| `ticketya.cache.timeout` | `EVENTOS_CACHE_TIMEOUT` | `PT1S` |
| `ticketya.cache.ttl-por-defecto` | `EVENTOS_CACHE_TTL_POR_DEFECTO` | `PT5M` |
| `ticketya.cache.ttl.<nombre>` | — | (usa el default) |

## Redis caído

La cache es una optimización: si Redis no está, `ms-eventos` sigue funcionando contra la base.

- **Arranque:** el cliente de Redisson se crea con inicialización diferida (se conecta al primer
  uso), así la aplicación levanta aunque Redis esté caído.
- **Crear una cache:** Redisson le manda un comando a Redis al crearla (lo hace también Actuator al
  registrar las métricas de las caches al iniciar). Si falla, se usa una cache que no guarda nada
  para esa llamada y se vuelve a intentar en la siguiente.
- **Leer, guardar o invalidar:** `CacheTolerante` (el `CacheErrorHandler`) registra un `WARN` y
  sigue; la llamada va a la base.
- **Timeouts cortos y sin reintentos** (1 s) para que un Redis caído no demore cada respuesta.

Se registra como `WARN Cache <nombre> no disponible …`.
