# ms-tickets: fila virtual de eventos de alta demanda

La fila virtual permite ordenar a los clientes por orden de llegada cuando un evento está marcado como de alta demanda. La cola vive en Redis y `ms-tickets` es el único servicio que la escribe y la lee.

## Flujo

1. El organizador crea o actualiza un evento con `altaDemanda: true` en `ms-eventos` (solo mientras el evento está en `BORRADOR`).
2. Al publicar el evento, `ms-eventos` envía `altaDemanda` a `ms-tickets` junto con la disponibilidad.
3. `ms-tickets` guarda el flag en `disponibilidad_eventos.alta_demanda`, deja la venta en estado `ABIERTA` y crea la configuración de habilitación por tandas.
4. Un cliente entra a la fila con `POST /api/tickets/eventos/{eventoId}/fila`.
5. Si el cliente vuelve a llamar, conserva su posición y recibe `200` en lugar de `201`.
6. Mientras espera, consulta su posición con `GET /api/tickets/eventos/{eventoId}/fila`; si ya no está en la fila recibe `404`.
7. Un job periódico de `ms-tickets` habilita a los primeros `N` clientes de la fila cada `intervalo` y les emite un pase con TTL. El `POST` y el `GET` de la fila pasan a devolver `estado: HABILITADO` con el pase.
8. Con el pase vigente, el cliente reserva normalmente; si el pase venció o el cliente no fue habilitado, la reserva responde `409`.
9. Si el cliente abandona la fila con `DELETE /api/tickets/eventos/{eventoId}/fila`, sale de la cola y se descarta su pase si ya había sido habilitado. Los demás clientes conservan su orden.
10. Si un pase vence sin que el cliente compre, el job lo descarta y habilita al siguiente cliente de la cola.
11. El cliente cuyo pase venció y todavía quiere comprar debe volver a entrar a la fila.

La venta se considera abierta solo si `disponibilidad_eventos.estado_venta` es `ABIERTA`. Si el evento no tiene disponibilidad en `ms-tickets`, la fila responde `404`; si la tiene pero `altaDemanda` es `false` o la venta no está abierta, responde `409`.

## Habilitación por tandas

La habilitación se activa automáticamente para los eventos de alta demanda cuando se publica su disponibilidad. El job `HabilitacionTandaJob` corre cada 5 segundos por defecto y, por cada evento:

1. Reclama la tanda con un `UPDATE ... WHERE proxima_tanda_el <= ahora` sobre `habilitacion_eventos`. Solo una instancia obtiene la fila; las demás no emiten pases en ese tick.
2. Ejecuta en Redis un único script Lua que hace `ZPOPMIN` de los primeros `N` clientes de la cola y crea un pase por cliente con `PEXPIREAT`. Además registra el vencimiento en un sorted set de pases activos.
3. Reprograma `proxima_tanda_el = ahora + intervalo`. Si Redis falla, vuelve a dejar la tanda para el siguiente tick.

Antes de reclamar la tanda, el job recorre los eventos habilitados y repone los pases vencidos. Un único script Lua elimina del sorted set de pases activos los que ya vencieron, borra sus claves y hace `ZPOPMIN` de la cola por la misma cantidad de lugares liberados. Así, si un pase vence sin uso, el siguiente cliente queda habilitado en el siguiente tick sin esperar al intervalo de la tanda. El `ZRANGEBYSCORE`, el `ZREM` y el `ZPOPMIN` son atómicos, por lo que varias instancias no reponen el mismo lugar dos veces.

Como la cola actúa de cursor (`ZPOPMIN`), los clientes pasan de `EN_ESPERA` a `HABILITADO` exactamente una vez: no se duplican entre instancias ni se saltean clientes, incluso si varias instancias ejecutan el job a la vez.

Parámetros por evento (valores iniciales tomados de la configuración global):

| Campo | Significado |
|---|---|
| `habilitada` | Si es `false`, el evento no exige pase y el job no emite tandas. |
| `tamano_tanda` | Cantidad de clientes habilitados por tanda (`N`). |
| `intervalo_segundos` | Segundos entre el inicio de una tanda y la siguiente. |
| `ttl_pase_segundos` | Segundos que un pase permanece válido desde su emisión. |
| `proxima_tanda_el` | Próximo instante en que el job puede emitir una tanda. También es el punto de coordinación entre instancias. |
| `total_habilitados` | Contador acumulado de pases emitidos. |

Se pueden ajustar por evento con el endpoint interno `PUT /api/tickets/internal/eventos/{eventoId}/habilitacion`. Como el gateway bloquea `/api/tickets/internal/**`, hay que llamar directo a la instancia de `ms-tickets`:

```bash
curl -i -X PUT http://localhost:8081/api/tickets/internal/eventos/5/habilitacion \
  -H "Content-Type: application/json" \
  -d '{"tamanoTanda": 20, "intervaloSegundos": 15, "ttlPaseSegundos": 120}'
```

El pase se valida al reservar solo cuando la habilitación global está activa, el evento es de alta demanda y tiene fila de habilitación activa. La autorización sigue siendo el `X-User-Id`; el `paseToken` del cuerpo es opcional y, si se envía, debe coincidir con el pase emitido para ese cliente. El pase no se consume al reservar: vale durante toda su ventana TTL.

### Claves en Redis

| Clave | Tipo | Contenido |
|---|---|---|
| `tickets:fila:evento:{eventoId}` | ZSET | Cola ordenada por llegada; el job extrae de acá con `ZPOPMIN`. |
| `tickets:pase:evento:{eventoId}:cliente:{clienteId}` | HASH con TTL | `clienteId`, `token`, `tanda`, `score`, `emitidoEl`, `expiraEl`. Redis borra la clave al vencer. |
| `tickets:pases-activos:evento:{eventoId}` | ZSET | Pases vigentes ordenados por `expiraEl`; el job los usa para reponer los vencidos. |

## Estado de venta

`ms-tickets` guarda el estado de venta en `disponibilidad_eventos.estado_venta`:

| Estado | Cuándo |
|---|---|
| `ABIERTA` | El evento se publicó y admite reservas e ingresos a la fila. |
| `AGOTADA` | `ms-eventos` marcó el evento como agotado. |
| `SUSPENDIDA` | `ms-eventos` suspendió el evento. |
| `CANCELADA` | `ms-eventos` canceló el evento. |
| `FINALIZADA` | `ms-eventos` finalizó el evento. |

`ms-eventos` notifica las transiciones de forma sincrónica antes de confirmar el nuevo estado. Si `ms-tickets` falla, `ms-eventos` devuelve `502` y no cambia el estado del evento.

### Endpoints internos

Estos endpoints son servicio-a-servicio, no requieren headers de usuario y son idempotentes. El gateway bloquea `/api/tickets/internal/**`, por lo que no están expuestos externamente.

| Método | Ruta | Efecto |
|---|---|---|
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/cancelar` | Pasa la venta a `CANCELADA`. |
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/agotar` | Pasa la venta a `AGOTADA`. |
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/suspender` | Pasa la venta a `SUSPENDIDA`. |
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/reactivar` | Vuelve la venta a `ABIERTA`. |
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/finalizar` | Pasa la venta a `FINALIZADA`. |
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/alta-demanda?altaDemanda={bool}` | Sincroniza el flag de alta demanda mientras el evento no está publicado. |
| `PUT` | `/api/tickets/internal/eventos/{eventoId}/habilitacion` | Ajusta `habilitada`, `tamanoTanda`, `intervaloSegundos` y `ttlPaseSegundos` de la habilitación por tandas. |
| `GET` | `/api/tickets/internal/eventos/{eventoId}/compradores` | Devuelve los `usuarioId` distintos que compraron entradas: `EMITIDO`, `USADO` o `DEVUELTO`. |

Los endpoints `PUT` responden `204 No Content`. Si el evento no tiene disponibilidad publicada responden `404`. El endpoint de compradores responde `200 OK` con una lista de ids ordenada de menor a mayor; si el evento no tiene compras devuelve `[]` (no `404`). No incluye tickets `RESERVADO`, `CANCELADO`, `EXPIRADO` ni `LIBERADO`, y descarta tickets sin `clienteId`.

## Endpoint

### `POST /api/tickets/eventos/{eventoId}/fila`

- Rol requerido: `CLIENTE`.
- El cliente se toma del header `X-User-Id`; no se acepta en el cuerpo.
- No requiere cuerpo.

Ejemplo:

```bash
curl -i -X POST http://localhost:8080/api/tickets/eventos/5/fila \
  -H "X-User-Id: 42" \
  -H "X-User-Role: CLIENTE"
```

Respuesta `201 Created` la primera vez:

```json
{
  "eventoId": 5,
  "clienteId": 42,
  "posicion": 1,
  "totalEnFila": 1,
  "yaEstabaEnFila": false,
  "ingresadoEl": "2026-11-10T14:03:21.123Z",
  "estado": "EN_ESPERA",
  "pase": null
}
```

Respuesta `200 OK` si el cliente ya estaba en la fila:

```json
{
  "eventoId": 5,
  "clienteId": 42,
  "posicion": 1,
  "totalEnFila": 3,
  "yaEstabaEnFila": true,
  "ingresadoEl": "2026-11-10T14:03:21.123Z",
  "estado": "EN_ESPERA",
  "pase": null
}
```

Respuesta `200 OK` si el cliente ya tiene un pase vigente (no se reinserta en la fila):

```json
{
  "eventoId": 5,
  "clienteId": 42,
  "posicion": null,
  "totalEnFila": 0,
  "yaEstabaEnFila": true,
  "ingresadoEl": "2026-11-10T14:05:00.000Z",
  "estado": "HABILITADO",
  "pase": {
    "token": "9f1c...:42",
    "tanda": "9f1c...",
    "expiraEl": "2026-11-10T14:07:00.000Z"
  }
}
```

### `DELETE /api/tickets/eventos/{eventoId}/fila`

- Rol requerido: `CLIENTE`.
- El cliente se toma del header `X-User-Id`; no se acepta en el cuerpo.
- No requiere cuerpo.
- Es idempotente: responde `204 No Content` aunque el cliente ya no estuviera en la fila.

Saca al cliente de la cola sin modificar el orden de los demás. Si el cliente ya tenía un pase, también lo descarta: deja de ocupar ese lugar y el job puede habilitar al siguiente.

Ejemplo:

```bash
curl -i -X DELETE http://localhost:8080/api/tickets/eventos/5/fila \
  -H "X-User-Id: 42" \
  -H "X-User-Role: CLIENTE"
```

Respuesta `204 No Content`, sin cuerpo.

### `GET /api/tickets/eventos/{eventoId}/fila`

- Rol requerido: `CLIENTE`.
- El cliente se toma del header `X-User-Id`; no se acepta en el cuerpo.
- No requiere cuerpo.

Pensado para el polling del cliente: solo lee Redis (pase y, si no tiene, `ZRANK` + `ZCARD`
en un único script Lua), sin consultar la base de datos ni validar el estado de venta. Si el
cliente ya fue habilitado responde `estado: HABILITADO` con el pase; si sigue esperando,
responde `EN_ESPERA` con su posición; si no está en la fila responde `404`.

Ejemplo:

```bash
curl -i http://localhost:8080/api/tickets/eventos/5/fila \
  -H "X-User-Id: 42" \
  -H "X-User-Role: CLIENTE"
```

Respuesta `200 OK`:

```json
{
  "eventoId": 5,
  "clienteId": 42,
  "posicion": 3,
  "personasDelante": 2,
  "totalEnFila": 7,
  "estado": "EN_ESPERA",
  "pase": null
}
```

`posicion` empieza en `1` y `personasDelante` es siempre `posicion - 1`.

Cuando el cliente ya fue habilitado, `posicion` y `personasDelante` son `null`:

```json
{
  "eventoId": 5,
  "clienteId": 42,
  "posicion": null,
  "personasDelante": null,
  "totalEnFila": 0,
  "estado": "HABILITADO",
  "pase": {
    "token": "9f1c...:42",
    "tanda": "9f1c...",
    "expiraEl": "2026-11-10T14:07:00.000Z"
  }
}
```

### Códigos de estado

| Código | Cuándo |
|---|---|
| `201` | `POST`: el cliente ingresó a la fila por primera vez. |
| `200` | `POST`: el cliente ya estaba en la fila o ya tiene un pase vigente. `GET`: el cliente está en la fila o tiene un pase. |
| `204` | `DELETE`: el cliente salió de la fila (o ya no estaba). |
| `401` | Falta la autenticación de usuario o `X-User-Id` no es numérico. |
| `403` | El rol no es `CLIENTE`. |
| `404` | `POST`: el evento no tiene disponibilidad publicada en `ms-tickets`. `GET`: el cliente no está en la fila ni tiene pase. |
| `409` | `POST`: el evento tiene disponibilidad pero no está marcado como `altaDemanda`, o la venta no está `ABIERTA`. |

## Modelo en Redis

| Elemento | Valor |
|---|---|
| Clave | `tickets:fila:evento:{eventoId}` |
| Tipo | Sorted set (`ZSET`) |
| Miembro | `{clienteId}` |
| Score | Momento de llegada en microsegundos según el reloj de Redis (`TIME`), calculado dentro de un script Lua |
| Posición | `ZRANK` + 1 (la primera posición es `1`) |

La entrada usa un script Lua atómico que hace `EXISTS` del pase, `ZSCORE`, `ZADD NX` y devuelve posición, total y momento de ingreso en una sola llamada. Si el miembro ya existe, Redis no lo vuelve a insertar ni modifica su score, por lo que la posición se conserva. La respuesta informa `yaEstabaEnFila` según el resultado de esa operación. Si el cliente ya tiene un pase se devuelve `HABILITADO` sin reinsertarlo: la comprobación del pase y el `ZADD` son atómicos respecto del `ZPOPMIN` de las tandas, así que un cliente nunca queda duplicado entre la cola y el pase.

Usar el reloj de Redis evita que dos instancias de `ms-tickets` con relojes desincronizados inviertan el orden de llegada. Para filas creadas por versiones anteriores con scores en milisegundos, la lectura convierte el score a microsegundos solo para informar `ingresadoEl`; la posición guardada no se altera.

Cuando dos clientes llegan en el mismo microsegundo, Redis desempata por orden lexicográfico del miembro. Es un empate prácticamente imposible y no altera la garantía de idempotencia.

Los pases viven en claves independientes con `PEXPIREAT`; Redis las elimina solas al vencer. La cola no tiene TTL: se conserva hasta que se borre la clave. El sorted set `tickets:pases-activos:evento:{eventoId}` guarda el vencimiento de cada pase emitido y lo limpia el job al reponer los vencidos; no tiene TTL propio.

## Configuración

`ms-tickets` usa Spring Data Redis con estas propiedades:

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` |
| `spring.data.redis.port` | `REDIS_PORT` | `6380` |
| `tickets.habilitacion.enabled` | `TICKETS_HABILITACION_ENABLED` | `true` |
| `tickets.habilitacion.tamano-tanda` | `TICKETS_HABILITACION_TAMANO_TANDA` | `50` |
| `tickets.habilitacion.intervalo` | `TICKETS_HABILITACION_INTERVALO` | `PT30S` |
| `tickets.habilitacion.ttl-pase` | `TICKETS_HABILITACION_TTL_PASE` | `PT5M` |
| `tickets.habilitacion.tick-cron` | `TICKETS_HABILITACION_TICK_CRON` | `*/5 * * * * *` |

`tamano-tanda`, `intervalo` y `ttl-pase` son los valores iniciales de cada evento; una vez creada la configuración, se pueden ajustar por evento con el endpoint interno. `enabled=false` desactiva el job y la exigencia de pases en todas las instancias.

La instancia de Redis del entorno local está definida en `infra/docker-compose.yml` como `redis-tickets` y expone el puerto `6380` en el host.

## Prueba manual

1. Levantar Redis: `docker compose -f infra/docker-compose.yml up -d redis-tickets`.
2. Publicar disponibilidad con `"altaDemanda": true` (carpeta `1. Disponibilidad` de Postman o el request equivalente).
3. Ejecutar la carpeta `3. Fila virtual` de la colección `postman/ms-tickets/ms-tickets.postman_collection.json`.
4. Para ver la habilitación, achicar la tanda y esperar el tick del job (llamando directo a `ms-tickets`, porque el gateway bloquea `/internal`):

```bash
curl -i -X PUT http://localhost:8081/api/tickets/internal/eventos/5/habilitacion \
  -H "Content-Type: application/json" \
  -d '{"tamanoTanda": 1, "intervaloSegundos": 5, "ttlPaseSegundos": 120}'
```

El `GET` de la fila del primer cliente pasa a devolver `estado: HABILITADO` con el pase. Para repetir la validación de `201` seguida de `200`, vaciar la fila antes de empezar:

```bash
redis-cli -p 6380 DEL tickets:fila:evento:5
```

También se puede inspeccionar la fila y los pases directamente:

```bash
redis-cli -p 6380 ZRANGE tickets:fila:evento:5 0 -1 WITHSCORES
redis-cli -p 6380 --scan --pattern 'tickets:pase:evento:5:*'
redis-cli -p 6380 ZRANGE tickets:pases-activos:evento:5 0 -1 WITHSCORES
```

Para probar el abandono, llamar `DELETE /api/tickets/eventos/5/fila` con un `X-User-Id` que esté en la fila y verificar que desaparece del sorted set. Para probar el vencimiento, ajustar un TTL corto (por ejemplo `{"ttlPaseSegundos": 5}`), esperar a que venza el pase y comprobar que el job habilita al siguiente cliente de la cola en el tick siguiente.

## Publicación de la confirmación de compra

Cuando la compra se confirma se publica `CompraConfirmadaNotificacionEvent` con `reservaId`. `NotificacionCompraEventListener` lo recibe con `@TransactionalEventListener(AFTER_COMMIT)` y agenda el envío en un executor dedicado de 2 hilos y cola 200; el worker publica en `enviar` con `mensajeId = MST-RESERVA-{reservaId}`.

Si Rabbit falla o el executor rechaza la tarea, se loguea el error con `mensajeId` y la compra ya confirmada no se revierte. Los timeouts de conexión, handshake y RPC son de 2 segundos. El procedimiento de medición y las variables de entorno están en `docs/api/publicacion-no-bloqueante.md`.
