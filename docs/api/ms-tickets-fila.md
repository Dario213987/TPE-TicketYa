# ms-tickets: fila virtual de eventos de alta demanda

La fila virtual permite ordenar a los clientes por orden de llegada cuando un evento está marcado como de alta demanda. La cola vive en Redis y `ms-tickets` es el único servicio que la escribe y la lee.

## Flujo

1. El organizador crea o actualiza un evento con `altaDemanda: true` en `ms-eventos` (solo mientras el evento está en `BORRADOR`).
2. Al publicar el evento, `ms-eventos` envía `altaDemanda` a `ms-tickets` junto con la disponibilidad.
3. `ms-tickets` guarda el flag en `disponibilidad_eventos.alta_demanda` y deja la venta en estado `ABIERTA`.
4. Un cliente entra a la fila con `POST /api/tickets/eventos/{eventoId}/fila`.
5. Si el cliente vuelve a llamar, conserva su posición y recibe `200` en lugar de `201`.

La venta se considera abierta solo si `disponibilidad_eventos.estado_venta` es `ABIERTA`. Si el evento no tiene disponibilidad en `ms-tickets`, la fila responde `404`; si la tiene pero `altaDemanda` es `false` o la venta no está abierta, responde `409`.

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

Todos responden `204 No Content`. Si el evento no tiene disponibilidad publicada responden `404`.

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
  "ingresadoEl": "2026-11-10T14:03:21.123Z"
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
  "ingresadoEl": "2026-11-10T14:03:21.123Z"
}
```

### Códigos de estado

| Código | Cuándo |
|---|---|
| `201` | El cliente ingresó a la fila por primera vez. |
| `200` | El cliente ya estaba en la fila; se conserva su posición. |
| `401` | Falta la autenticación de usuario o `X-User-Id` no es numérico. |
| `403` | El rol no es `CLIENTE`. |
| `404` | El evento no tiene disponibilidad publicada en `ms-tickets`. |
| `409` | El evento tiene disponibilidad pero no está marcado como `altaDemanda`, o la venta no está `ABIERTA`. |

## Modelo en Redis

| Elemento | Valor |
|---|---|
| Clave | `tickets:fila:evento:{eventoId}` |
| Tipo | Sorted set (`ZSET`) |
| Miembro | `{clienteId}` |
| Score | Momento de llegada en microsegundos según el reloj de Redis (`TIME`), calculado dentro de un script Lua |
| Posición | `ZRANK` + 1 (la primera posición es `1`) |

La entrada usa un script Lua atómico que hace `ZSCORE`, `ZADD NX` y devuelve posición, total y momento de ingreso en una sola llamada. Si el miembro ya existe, Redis no lo vuelve a insertar ni modifica su score, por lo que la posición se conserva. La respuesta informa `yaEstabaEnFila` según el resultado de esa operación.

Usar el reloj de Redis evita que dos instancias de `ms-tickets` con relojes desincronizados inviertan el orden de llegada. Para filas creadas por versiones anteriores con scores en milisegundos, la lectura convierte el score a microsegundos solo para informar `ingresadoEl`; la posición guardada no se altera.

Cuando dos clientes llegan en el mismo microsegundo, Redis desempata por orden lexicográfico del miembro. Es un empate prácticamente imposible y no altera la garantía de idempotencia.

La cola no tiene TTL: se conserva hasta que se borre la clave. Tampoco existe todavía un endpoint para abandonar la fila o para que el sistema entregue turnos; esta US solo registra el orden de llegada.

## Configuración

`ms-tickets` usa Spring Data Redis con estas propiedades:

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` |
| `spring.data.redis.port` | `REDIS_PORT` | `6380` |

La instancia de Redis del entorno local está definida en `infra/docker-compose.yml` como `redis-tickets` y expone el puerto `6380` en el host.

## Prueba manual

1. Levantar Redis: `docker compose -f infra/docker-compose.yml up -d redis-tickets`.
2. Publicar disponibilidad con `"altaDemanda": true` (carpeta `1. Disponibilidad` de Postman o el request equivalente).
3. Ejecutar la carpeta `3. Fila virtual` de la colección `postman/ms-tickets/ms-tickets.postman_collection.json`.
4. Para repetir la validación de `201` seguida de `200`, vaciar la fila antes de empezar:

```bash
redis-cli -p 6380 DEL tickets:fila:evento:5
```

También se puede inspeccionar la fila directamente:

```bash
redis-cli -p 6380 ZRANGE tickets:fila:evento:5 0 -1 WITHSCORES
```
