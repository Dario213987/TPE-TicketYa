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
| 3. Emitir | `ReservaPagoService.emitir` (transacción) | Tickets y asientos a `VENDIDO`, reserva a `APROBADO`. |
| Compensar | `PagoService.compensar` + `ReservaPagoService.liberar` | Crédito por cada débito aprobado y liberación de los lugares. |

## Estados de la reserva (`reservas.estado_pago`)

```
              preparar
 (sin pago) ───────────► PENDIENTE_PAGO ──── débitos OK + emisión OK ────► APROBADO
                              │   ▲
                              │   └── timeout / 5xx de ms-transacciones: queda igual;
                              │       el reintento con la misma Idempotency-Key consulta
                              │       el estado real (los débitos son idempotentes)
                              │
                              ├── algún débito rechazado ──► reintegro de los aprobados ──► RECHAZADO
                              │
                              ├── falla la emisión ────────► reintegro de todos ─────────► REINTEGRADO
                              │
                              └── falla algún reintegro ─────────────────────────────────► REINTEGRO_PENDIENTE
```

| Estado | Significado | Lugares | Respuesta |
|---|---|---|---|
| `PENDIENTE_PAGO` | Se pidió el cobro y todavía no hay un resultado definitivo. | Tomados: los tickets `PENDIENTE_PAGO` no se liberan por vencimiento. | 202 |
| `APROBADO` | Cobrado y emitido. | Vendidos | 201 |
| `RECHAZADO` | ms-transacciones rechazó un débito. Los débitos que se habían aprobado se reintegraron. | Liberados | 409 con el motivo |
| `REINTEGRADO` | Se cobró pero falló la emisión. Se reintegró todo. | Liberados | 409 |
| `REINTEGRO_PENDIENTE` | Falló la emisión o un rechazo parcial, y además falló el reintegro de algún débito. Hay que resolverlo a mano. | Liberados (para no trabar lugares) | 409 |

Un reintento con la misma `Idempotency-Key` sobre un estado final devuelve el mismo resultado
sin volver a cobrar.

## Estados del ticket (`tickets.estado_ticket`)

`RESERVADO` → `PENDIENTE_PAGO` → `VENDIDO`. Si se compensa, `PENDIENTE_PAGO` → `CANCELADO` y el
asiento vuelve a `LIBRE`. Los tickets `PENDIENTE_PAGO` cuentan para el límite por cliente.

## Log

Toda compensación se registra en el log de `PagoService`:

- `WARN Compensación: reserva=… ticket=… monto=… reintegrado (transacción …)`
- `ERROR Compensación fallida: reserva=… ticket=… monto=…`, cuando no se pudo reintegrar.
- `WARN Compensación: reserva=… liberada, estado …`
- `WARN Pago pendiente: reserva=… ticket=…`, en un timeout, que no se compensa.

## Pendiente para la Saga (E2)

- Reintentar automáticamente los `REINTEGRO_PENDIENTE` y los `PENDIENTE_PAGO` abandonados, que
  hoy solo avanzan con un reintento del cliente.
- Persistir cada paso (outbox) en lugar de depender del log.
- Consultar el estado de las transacciones por ticket en lugar de repetir el débito idempotente.
