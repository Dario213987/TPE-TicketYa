# ms-eventos: endpoints internos y finalización automática

Los endpoints internos de `ms-eventos` son llamados servicio-a-servicio (por ejemplo, desde `ms-tickets`) y no requieren autenticación de usuario. El `api-gateway` bloquea `/api/eventos/internal/**`, por lo que no deben consumirse a través del puerto público del gateway. Para pruebas manuales usar la URL directa de `ms-eventos` (variable `internalBaseUrl` de la colección Postman).

## Endpoints

### PUT /api/eventos/internal/{id}/estado/agotado

Marca un evento `PUBLICADO` como `AGOTADO`. Es idempotente: si el evento ya está `AGOTADO`, devuelve su estado sin cambios.

- Respuesta `200 OK`: `EventoEstadoResponse` (`id`, `nombre`, `estado`).
- `404`: el evento no existe.
- `409`: el estado actual no permite la transición.

### PUT /api/eventos/internal/{id}/estado/publicar

Vuelve a publicar un evento `AGOTADO` (`AGOTADO` -> `PUBLICADO`). Es idempotente si ya está `PUBLICADO`.

- Respuesta `200 OK`: `EventoEstadoResponse`.
- `404`: el evento no existe.
- `409`: el estado actual no permite la transición.

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

### Configuración

| Propiedad | Variable de entorno | Default | Descripción |
|---|---|---|---|
| `ticketya.eventos.finalizacion.enabled` | `EVENTOS_FINALIZACION_ENABLED` | `true` | Activa o desactiva el scheduler. |
| `ticketya.eventos.finalizacion.cron` | `EVENTOS_FINALIZACION_CRON` | `0 */15 * * * *` | Expresión cron. |
| `ticketya.eventos.finalizacion.horas-gracia` | `EVENTOS_FINALIZACION_HORAS_GRACIA` | `6` | Horas de gracia después de la fecha del evento. |
| `ticketya.eventos.finalizacion.lock-at-most-for` | `EVENTOS_FINALIZACION_LOCK_AT_MOST_FOR` | `PT15M` | Tiempo máximo de bloqueo de ShedLock. |
| `ticketya.eventos.finalizacion.lock-at-least-for` | `EVENTOS_FINALIZACION_LOCK_AT_LEAST_FOR` | `PT1M` | Tiempo mínimo de bloqueo de ShedLock. |

## Fecha de evento

`fecha` pasó de `date` a `date-time` en el contrato (`LocalDateTime` en Java). La migración `00012-cambiar-fecha-evento-a-timestamp.xml` convierte los datos existentes:

- PostgreSQL/H2: `fecha + TIME '23:59:59'` (los eventos viejos se consideran vencidos al final de su día).
- MySQL: `TIMESTAMP(DATE(fecha), '23:59:59')`.

La tabla `evento_notificacion` también usa `timestamp with time zone` en PostgreSQL/H2 y `datetime(6)` en MySQL.

## Outbox e idempotencia

El envío de notificaciones a `ms-tickets` es at-least-once. `ms-tickets` debe ser idempotente al recibir una notificación. La tabla de outbox tiene una restricción única `(evento_id, tipo)`: si una notificación quedó en estado `FALLIDO`, no se vuelve a encolar automáticamente. Para reintentarla hay que hacerlo de forma operativa (por ejemplo, cambiar su estado o reprocesarla), no esperar que el job la vuelva a crear.

## Prueba manual

Con `ms-eventos` levantado en un puerto conocido (o tomado de Eureka):

```bash
curl -X PUT http://localhost:<puerto-ms-eventos>/api/eventos/internal/1/estado/agotado
curl -X PUT http://localhost:<puerto-ms-eventos>/api/eventos/internal/1/estado/publicar
curl -i -X PUT http://localhost:<puerto-ms-eventos>/api/eventos/internal/finalizar-vencidos
```

En Postman usar la carpeta `3. internos` y la variable `internalBaseUrl`. No usar `baseUrl` (gateway): el gateway bloquea `/api/eventos/internal/**`.
