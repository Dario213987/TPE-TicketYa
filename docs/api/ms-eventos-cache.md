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

## Qué se cachea

### Detalle de evento (TKY-82)

| | |
|---|---|
| **Endpoint** | `GET /api/eventos/{eventoId}` |
| **Dato** | `EventoDetalleResponse` (el DTO de respuesta, nunca la entidad: tiene relaciones lazy) |
| **Dónde** | Redis `redis-eventos`, cache `evento-detalle` (`RMapCache` de Redisson), clave `eventoId` |
| **TTL** | 5 min (`ticketya.cache.ttl.evento-detalle`, variable `EVENTOS_CACHE_TTL_EVENTO_DETALLE`) |
| **Quién lo maneja** | `EventoDetalleCache` (`@Cacheable`); `EventoService.obtenerEvento` lo usa |

**La visibilidad no se cachea:** se cachea el detalle tal como está en la base, sin importar quién
lo pide. `EventoService.obtenerEvento` aplica después la regla de borradores (solo el organizador
dueño o un administrador ven un `BORRADOR`), así un borrador cacheado nunca se le muestra a otro.

**Se invalida** (`@CacheEvict` con la clave del evento) en cada operación de `EventoService` que lo
modifica:

| Operación | Endpoint |
|---|---|
| Editar | `PUT /api/eventos/{id}` |
| Publicar | `PUT /api/eventos/{id}/publicar` |
| Suspender / reactivar | `PUT /api/eventos/{id}/suspender`, `/reactivar` |
| Cancelar | `POST /api/eventos/{id}/cancelar` |
| Eliminar | `DELETE /api/eventos/{id}` |
| Precio de un sector | `PUT /api/eventos/{id}/sectores/{sectorId}/precio` |
| Agotado / publicado (desde ms-tickets) | `PUT /api/eventos/internal/{id}/estado/agotado`, `/publicar` |
| Finalización automática | job de eventos vencidos: invalida cada evento que finaliza |

- **Después del commit:** las invalidaciones y escrituras se aplican cuando se confirma la
  transacción (`TransactionAwareCacheDecorator`), para que un pedido simultáneo no vuelva a guardar
  el dato viejo. Si la operación falla, no se invalida.
- **Límite conocido:** editar un **lugar o un sector** cambia el detalle de todos los eventos de
  ese lugar; no se invalidan uno por uno, así que la inconsistencia máxima es el TTL (5 min).

## Redis caído

La cache es una optimización: si Redis no está, `ms-eventos` sigue funcionando contra la base.

- **Arranque:** el cliente de Redisson se crea con inicialización diferida (se conecta al primer
  uso), así la aplicación levanta aunque Redis esté caído.
- **Crear una cache:** Redisson le manda un comando a Redis al crearla (lo hace también Actuator al
  registrar las métricas de las caches al iniciar). Si falla, se usa una cache que no guarda nada
  para esa llamada y se vuelve a intentar en la siguiente.
- **Leer, guardar o invalidar:** cada cache va envuelta en `CacheSegura`, que registra un `WARN` y
  sigue (también en las escrituras e invalidaciones que corren después del commit, fuera del
  `CacheErrorHandler`); la llamada va a la base.
- **Timeouts cortos y sin reintentos** (1 s) para que un Redis caído no demore cada respuesta.

Se registra como `WARN Cache <nombre> no disponible …`.
