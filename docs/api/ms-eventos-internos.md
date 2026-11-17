# ms-eventos: endpoints internos, finalización automática y notificaciones

Los endpoints internos de `ms-eventos` son llamados servicio-a-servicio (por ejemplo, desde `ms-tickets`) y no requieren autenticación de usuario. El `api-gateway` bloquea `/api/eventos/internal` y `/api/eventos/internal/**`, por lo que no deben consumirse a través del puerto público del gateway. Para pruebas manuales usar la URL directa de `ms-eventos` (variable `internalBaseUrl` de la colección Postman).

## Endpoints

### PUT /api/eventos/internal/{id}/estado/agotado

Marca un evento `PUBLICADO` como `AGOTADO` y notifica a `ms-tickets` para que la venta pase a `AGOTADA`. Es idempotente: si el evento ya está `AGOTADO`, devuelve su estado sin cambios.

- Respuesta `200 OK`: `EventoEstadoResponse` (`id`, `nombre`, `estado`).
- `404`: el evento no existe.
- `409`: el estado actual no permite la transición.
- `502`: `ms-tickets` no está disponible; el evento no cambia de estado.

### PUT /api/eventos/internal/{id}/estado/publicar

Vuelve a publicar un evento `AGOTADO` (`AGOTADO` -> `PUBLICADO`) y notifica a `ms-tickets` para que la venta vuelva a `ABIERTA`. Es idempotente si ya está `PUBLICADO`.

- Respuesta `200 OK`: `EventoEstadoResponse`.
- `404`: el evento no existe.
- `409`: el estado actual no permite la transición.
- `502`: `ms-tickets` no está disponible; el evento no cambia de estado.

### PUT /api/eventos/internal/finalizar-vencidos

Dispara manualmente el mismo proceso que ejecuta el scheduler. Pasa a `FINALIZADO` los eventos en estado `PUBLICADO`, `AGOTADO` o `SUSPENDIDO` cuya `fecha` sea anterior a `ahora - 6 horas`.

- Respuesta `204 No Content`.
- No requiere cuerpo.

## Finalización automática

- Scheduler: `EventoFinalizacionScheduler`, cron por defecto `0 */15 * * * *` (cada 15 minutos).
- Regla: `fecha < LocalDateTime.now().minusHours(horasGracia)`.
- Estados finalizables: `PUBLICADO`, `AGOTADO`, `SUSPENDIDO`.
- ShedLock evita ejecuciones concurrentes entre instancias de `ms-eventos`.
- Al finalizar, se encola una notificación `FINALIZAR` en el outbox para `ms-tickets`.
- La cancelación y el cambio de fecha/lugar publican un aviso asíncrono directo a `ms-notificaciones`, después del commit, sin pasar por el outbox.
- Las transiciones `cancelar`, `suspender`, `reactivar`, `agotar` y `publicar` se notifican a `ms-tickets` de forma síncrona, antes de confirmar el nuevo estado. Si `ms-tickets` falla, la operación devuelve `502` y el estado no cambia.
- En `cancelar` además se llama de forma síncrona a `ms-devoluciones`; si falla, tampoco se persiste `CANCELADO` ni se publica el aviso.

### Configuración

| Propiedad | Variable de entorno | Default | Descripción |
|---|---|---|---|
| `ticketya.eventos.finalizacion.enabled` | `EVENTOS_FINALIZACION_ENABLED` | `true` | Activa o desactiva el scheduler. |
| `ticketya.eventos.finalizacion.cron` | `EVENTOS_FINALIZACION_CRON` | `0 */15 * * * *` | Expresión cron. |
| `ticketya.eventos.finalizacion.horas-gracia` | `EVENTOS_FINALIZACION_HORAS_GRACIA` | `6` | Horas de gracia después de la fecha del evento. |
| `ticketya.eventos.finalizacion.lock-at-most-for` | `EVENTOS_FINALIZACION_LOCK_AT_MOST_FOR` | `PT15M` | Tiempo máximo de bloqueo de ShedLock. |
| `ticketya.eventos.finalizacion.lock-at-least-for` | `EVENTOS_FINALIZACION_LOCK_AT_LEAST_FOR` | `PT1M` | Tiempo mínimo de bloqueo de ShedLock. |

## Avisos asíncronos a ms-notificaciones

`ms-eventos` publica avisos para los compradores sólo después de que la transacción que cambia el estado hace commit. Es el único destino asíncrono de este servicio; `ms-tickets` y `ms-devoluciones` se siguen notificando por HTTP.

1. Un organizador publica un evento en borrador, un administrador lo cancela o cambia su fecha/lugar (el cambio de fecha/lugar en eventos no borrador es una excepción reservada a administradores; los estados válidos son `PUBLICADO`, `AGOTADO` y `SUSPENDIDO`).
2. `ms-eventos` notifica sincrónicamente a `ms-tickets` (`crearDisponibilidad` más `actualizarAltaDemanda` en la publicación, `cancelar` en la cancelación) y, en la cancelación, a `ms-devoluciones` (`reintegrar`).
3. Dentro de la transacción se publica un `AvisoEventoEvent` con:
   - `motivo`: `PUBLICACION_ARTISTA`, `CANCELACION` o `EDICION`;
   - `eventoId`;
   - `mensajeId`: `MSE-PUBLICACION_ARTISTA-{eventoId}` y `MSE-CANCELACION-{eventoId}` (estables, porque un evento se publica y se cancela una sola vez) o `MSE-EDICION-{timestamp}-{uuid}` (uno por cambio).
4. `AvisoEventoListener` publica el mensaje en la cola `enviar` con `@TransactionalEventListener(AFTER_COMMIT)`, con `destinatarioId: null`.
5. Para `CANCELACION` y `EDICION`, `ms-notificaciones` consulta `GET /api/tickets/internal/eventos/{eventoId}/compradores` y envía un email por comprador: el de cancelación indica que el reintegro ya fue solicitado, el de edición muestra la fecha/lugar actualizados. Para `PUBLICACION_ARTISTA`, `ms-notificaciones` obtiene de `ms-eventos` la ciudad del lugar y los ids de participantes, y consulta `GET /api/preferencias/internal/destinatarios` para enviar el mail a los clientes que tienen alguno de esos participantes como favorito y declararon la misma ciudad; no usa compras previas.

No se duplican envíos por destinatario: el `mensajeId` y la restricción única `(mensaje_id, destinatario_id)` en `ms-notificaciones` evitan duplicados ante reintentos del mismo mensaje. La publicación no tiene reintento automático: si Rabbit falla, se registra el error en logs y requiere reproceso operativo.

La publicación usa las mismas colas y argumentos que `ms-notificaciones`: cola `enviar` con DLX `enviar.dlx` y DLQ `enviar.dlq`. El mensaje se serializa como JSON.

## Fecha de evento

`fecha` pasó de `date` a `date-time` en el contrato (`LocalDateTime` en Java). La migración `00012-cambiar-fecha-evento-a-timestamp.xml` convierte los datos existentes:

- PostgreSQL: `fecha + TIME '23:59:59'` (los eventos viejos se consideran vencidos al final de su día).
- MySQL: `TIMESTAMP(DATE(fecha), '23:59:59')`.

La tabla `evento_notificacion` también usa `timestamp with time zone` en PostgreSQL y `datetime(6)` en MySQL. Los tests de integración de migraciones se ejecutan contra PostgreSQL con Testcontainers; MySQL no está cubierto por esos tests.

## Outbox e idempotencia

El outbox se usa únicamente para la notificación `FINALIZAR`, porque nace de un proceso programado y no de una request de usuario. `cancelar`, `suspender` y `reactivar` son llamadas síncronas a `ms-tickets`.

El alta de la fila ocurre en la misma transacción que el cambio de estado. `encolar` no crea una fila si ya existe una para `(eventoId, tipo)`. El relay corre cada 60 segundos por defecto, procesa hasta 50 filas y reintenta hasta 5 veces con backoff de 60s, 300s, 900s y 3600s. Si `ms-tickets` falla, la fila queda `PENDIENTE` con un `proximoIntento` futuro. Al llegar al máximo queda `FALLIDO` y no se vuelve a encolar automáticamente: hay que reprocesarla de forma operativa.

El envío es at-least-once. `ms-tickets` debe ser idempotente al recibir una notificación. Los avisos a `ms-notificaciones` no usan el outbox y se publican después del commit.

### Configuración RabbitMQ

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `spring.rabbitmq.host` | `RABBITMQ_HOST` | `localhost` |
| `spring.rabbitmq.port` | `RABBITMQ_PORT` | `5672` |
| `spring.rabbitmq.username` | `RABBITMQ_USER` | `ticketya` |
| `spring.rabbitmq.password` | `RABBITMQ_PASSWORD` | `guest` |
| `spring.rabbitmq.virtual-host` | `RABBITMQ_VHOST` | `/` |

El relay se controla con `ticketya.eventos.notificaciones.*` (`EVENTOS_NOTIFICACIONES_ENABLED`, `EVENTOS_NOTIFICACIONES_FIXED_DELAY_MS`, `EVENTOS_NOTIFICACIONES_BATCH_SIZE`, `EVENTOS_NOTIFICACIONES_MAX_INTENTOS`).

## Prueba manual

Con `ms-eventos` levantado en un puerto conocido (o tomado de Eureka):

```bash
curl -X PUT http://localhost:<puerto-ms-eventos>/api/eventos/internal/1/estado/agotado
curl -X PUT http://localhost:<puerto-ms-eventos>/api/eventos/internal/1/estado/publicar
curl -i -X PUT http://localhost:<puerto-ms-eventos>/api/eventos/internal/finalizar-vencidos
```

En Postman usar la carpeta `3. internos` y la variable `internalBaseUrl`. No usar `baseUrl` (gateway): el gateway bloquea `/api/eventos/internal/**`.
