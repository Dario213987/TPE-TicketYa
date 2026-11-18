# Estados de la compra (ms-tickets)

Cómo avanza el pago de una reserva (`POST /api/tickets/reservas/{reservaId}/pago`) y qué se
compensa cuando un paso falla. Es la versión básica de la entrega 1 (TKY-56 y TKY-57). En la
entrega 2 se refina con una Saga.

## Pasos

El pago se divide en pasos con transacciones locales separadas. Así, las llamadas a
ms-transacciones se hacen sin tener locks de la base tomados, y se puede compensar si un paso
posterior falla.

| Paso | Dónde | Qué hace |
|---|---|---|
| 1. Preparar | `ReservaPagoService.preparar` (transacción) | Valida la reserva, la clave y los precios. Pasa la reserva y sus tickets a `PENDIENTE_PAGO`. |
| 2. Cobrar | `PagoService` (sin transacción) | Un débito por ticket en ms-transacciones, a nombre del organizador. |
| 3. Emitir | `ReservaPagoService.emitir` (transacción) | Tickets y asientos a `VENDIDO`, reserva a `APROBADO`, registro de la venta con sus ítems (TKY-62) y evento `CompraConfirmadaNotificacionEvent` para `ms-notificaciones`. |
| Compensar | `PagoService.compensar` + `ReservaPagoService.liberar` | Crédito por cada débito aprobado y liberación de los lugares. |

## Estados de la reserva (`reservas.estado_pago`)

```
              preparar
 (sin pago) ───────────► PENDIENTE_PAGO ──── débitos OK + emisión OK ────► APROBADO
                              │   ▲
                              │   ├── timeout / 5xx de ms-transacciones (después de reintentar),
                              │   │   o débito todavía PENDIENTE: queda igual (202); el reintento con la misma
                              │   │   Idempotency-Key consulta el estado real (los débitos son
                              │   │   idempotentes)
                              │   └── 4xx de ms-transacciones (error de integración): queda
                              │       igual, no se compensa y responde 502
                              │
                              ├── algún débito FALLIDA ────► reintegro de los aprobados ──► RECHAZADO
                              │
                              ├── falla la emisión ────────► reintegro de todos ─────────► REINTEGRADO
                              │
                              └── falla algún reintegro ─────────────────────────────────► REINTEGRO_PENDIENTE
```

| Estado | Significado | Lugares | Respuesta |
|---|---|---|---|
| `PENDIENTE_PAGO` | Se pidió el cobro y todavía no hay un resultado definitivo. | Tomados: los tickets `PENDIENTE_PAGO` no se liberan por vencimiento. | 202 (502 si ms-transacciones rechazó el pedido con un 4xx) |
| `APROBADO` | Cobrado y emitido. | Vendidos | 201 |
| `RECHAZADO` | El procesador rechazó un débito (`FALLIDA`). Los débitos que se habían aprobado se reintegraron. | Liberados | 409 con el motivo |
| `REINTEGRADO` | Se cobró pero falló la emisión. Se reintegró todo. | Liberados | 409 |
| `REINTEGRO_PENDIENTE` | Falló la emisión o un rechazo parcial, y además falló el reintegro de algún débito. Hay que resolverlo a mano. | Liberados (para no trabar lugares) | 409 |

Un reintento con la misma `Idempotency-Key` sobre un estado final devuelve el mismo resultado
sin volver a cobrar. Un reintento sobre `PENDIENTE_PAGO` conserva el medio de pago del primer
intento.

La transición a `APROBADO` dispara la notificación de compra por Rabbit (`AFTER_COMMIT`). El
`mensajeId` se deriva de `reservaId` (`MST-RESERVA-{id}`), por lo que un hook repetido no genera
otra notificación.

Un 4xx de ms-transacciones (headers, monto u organizador inválidos) no es un rechazo del pago:
se reintenta con la misma clave cuando se corrige la integración.

El medio de pago es un valor cerrado (`TARJETA_CREDITO`, `TARJETA_DEBITO`), así nunca se guardan
datos de la tarjeta (US-05.07).

## Estados del ticket (`tickets.estado_ticket`)

`RESERVADO` → `PENDIENTE_PAGO` → `VENDIDO`. Si se compensa, `PENDIENTE_PAGO` → `CANCELADO` y el
asiento vuelve a `LIBRE`. Los tickets `PENDIENTE_PAGO` cuentan para el límite por cliente.

## Timeouts y reintentos (TKY-85)

| Tramo | Timeout | Intentos | Propiedades |
|---|---|---|---|
| ms-tickets → ms-transacciones | 2 s conexión, 7 s lectura | 2, backoff 300 ms | `spring.cloud.openfeign.client.config.transacciones.connect-timeout` / `read-timeout`, `tickets.transacciones.max-intentos` / `backoff-inicial-ms` |
| ms-transacciones → pasarela | 2 s por intento | 3, backoff 200 → 400 ms | `ticketya.pasarela.timeout-ms` / `max-intentos` / `backoff-inicial-ms` |

- Solo se reintentan los fallos técnicos: timeout o error de conexión y 5xx. Un 4xx de
  ms-transacciones o un rechazo de la pasarela no se reintentan.
- Débitos y créditos son idempotentes en ms-transacciones: repetir el pedido devuelve la misma
  transacción, y si quedó `PENDIENTE` se reprocesa en la pasarela con la misma clave (`tx-<id>`).
- Los tiempos encadenan: ms-transacciones se rinde en ~6,6 s, por eso ms-tickets espera 7 s, y
  los 2 intentos de ms-tickets (~14,3 s) entran en los 15 s del `response-timeout` del gateway.
- Los reintentos se registran como `WARN Reintento N de ms-transacciones en … ms`.

## Log

Toda compensación se registra en el log de `PagoService`:

- `WARN Compensación: reserva=… ticket=… monto=… reintegrado (transacción …)`
- `ERROR Compensación fallida: reserva=… ticket=… monto=…`, cuando no se pudo reintegrar.
- `WARN Compensación: reserva=… liberada, estado …`
- `WARN Pago pendiente: reserva=… ticket=…`, en un timeout o débito PENDIENTE, que no se compensa.
- `ERROR Error de integración con ms-transacciones: reserva=… ticket=…`, en un 4xx, que no se compensa.

## Pendiente para la Saga (E2)

- Reintentar automáticamente los `REINTEGRO_PENDIENTE` y los `PENDIENTE_PAGO` abandonados, que
  hoy solo avanzan con un reintento del cliente.
- Persistir cada paso (outbox) en lugar de depender del log.
- Consultar el estado de las transacciones por ticket en lugar de repetir el débito idempotente.
