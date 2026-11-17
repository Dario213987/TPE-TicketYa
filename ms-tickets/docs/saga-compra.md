# Saga de compra y consistencia (TKY-87)

La compra atraviesa dos servicios (ms-tickets y ms-transacciones) y avisa a un tercero
(ms-notificaciones). No hay una transacción distribuida: cada servicio confirma su parte en una
transacción local y, si un paso posterior falla, se deshace lo anterior con una **acción
compensatoria**. Es una saga **orquestada** por ms-tickets (`PagoService`).

Los estados de la reserva y del ticket están en [estados-compra.md](estados-compra.md).

## Pasos y compensaciones

| # | Paso | Dónde | Transacción | Si falla un paso posterior, se compensa con |
|---|---|---|---|---|
| 1 | **Reservar**: toma stock (capacidad o asientos) y crea la reserva con vencimiento | ms-tickets, `ReservaService.reservar` | Local, con lock del sector/asientos | **Liberar**: los tickets pasan a `CANCELADO` (o `LIBERADO` si vence) y el stock vuelve a estar disponible (`ReservaVencidaService.liberar`) |
| 2 | **Preparar el pago**: reserva y tickets a `PENDIENTE_PAGO`, guarda la `Idempotency-Key` y el medio de pago | ms-tickets, `ReservaPagoService.preparar` | Local, con lock de la reserva | Se deshace junto con el paso 1: `liberar` vuelve los tickets y pone el estado final de la reserva |
| 3 | **Cobrar**: un débito por ticket | ms-transacciones (llamado desde `PagoService`, sin locks tomados) | Local en ms-transacciones | **Reintegrar**: un crédito por cada débito aprobado (`PagoService.compensar`) |
| 4 | **Emitir**: tickets y asientos a `VENDIDO`, reserva `APROBADO` y registro de la venta | ms-tickets, `ReservaPagoService.emitir` | Local | Es el último paso: si falla, se reintegran los débitos (3) y se libera la reserva (1) → `REINTEGRADO` |
| 5 | **Notificar** la confirmación | ms-tickets → RabbitMQ → ms-notificaciones (después del commit de 4) | Asíncrono | No se compensa: no deshace la compra. Si falla la publicación, se registra en el log |

Cuándo se compensa:

| Situación | Compensación | Estado final |
|---|---|---|
| Un débito rechazado (`FALLIDA`), incluso con el circuito de la pasarela abierto | Reintegro de los débitos aprobados + liberar | `RECHAZADO` (409) |
| Falla la emisión con el pago aprobado | Reintegro de todos los débitos + liberar | `REINTEGRADO` (409) |
| Falla algún reintegro | Se libera igual (no se traban lugares) | `REINTEGRO_PENDIENTE` (se resuelve a mano) |
| Timeout, 5xx o débito `PENDIENTE` en ms-transacciones | **No** se compensa: no se sabe si se cobró | `PENDIENTE_PAGO` (202), lo resuelve la reconciliación |

## Reconciliación de pagos pendientes

`ReconciliacionPagoJob` resuelve las reservas que quedaron en `PENDIENTE_PAGO` consultando el
estado real de cada débito en ms-transacciones (`GET /api/transacciones/ticket/{ticketId}?tipo=DEBITO`,
como organizador del evento) y aplicando la misma lógica que el pago en línea
(`PagoService.reconciliar`).

- Corre cada minuto (`tickets.reconciliacion.cron`) y solo en una instancia (bloqueo
  `reconciliar-pagos` en `bloqueos_job`, como la liberación de reservas vencidas).
- Solo toma reservas vencidas hace más de `tickets.reconciliacion.margen` (1 min): para entonces
  ningún pago en línea las está procesando.

| Estado real de los débitos | Qué hace |
|---|---|
| Todos `COMPLETA` | Emite las entradas → `APROBADO` (y sale la notificación) |
| Alguno `FALLIDA` | Reintegra los aprobados y libera → `RECHAZADO` |
| Alguno `PENDIENTE` | Repite el débito (idempotente: se reprocesa con la misma clave en la pasarela). Si sigue `PENDIENTE`, no cambia nada y queda para la próxima vuelta |
| Algún ticket sin débito del cliente | Ese cobro nunca se registró: reintegra los aprobados y libera → `RECHAZADO` ("el pago no se confirmó en ms-transacciones") |
| ms-transacciones no responde | No cambia nada; queda para la próxima vuelta |

Se registra en el log como `Reconciliación: reserva=… …` y un resumen por vuelta.

El contrato de ms-transacciones mapea la página de esa consulta a `PagedModel` de Spring Data, que
Jackson no sabe construir; `PagedModelJacksonConfig` agrega el deserializador en ms-tickets.

## Consistencia fuerte y eventual

**Fuerte (dentro de ms-tickets):** el stock y el estado de la compra.

- Reservar descuenta capacidad o toma asientos en una transacción con lock pesimista del sector y
  de los asientos (`DisponibilidadSectorRepository`, `findByIdForUpdate`), y la tabla tiene
  restricciones únicas (un asiento, un ticket). Dos clientes nunca obtienen el mismo lugar ni se
  vende por encima de la capacidad (pruebas de no doble venta, TKY-64).
- Cada paso del pago cambia reserva, tickets y asientos juntos en una sola transacción local, con
  lock de la reserva: nunca queda un ticket `VENDIDO` en una reserva sin aprobar.
- La `Idempotency-Key` es única por cliente (índice `uk_reservas_cliente_idempotency_key`).

**Eventual (entre servicios):** el cobro y los avisos.

- **ms-tickets ↔ ms-transacciones:** el débito puede quedar aprobado en ms-transacciones mientras
  la reserva sigue `PENDIENTE_PAGO` (timeout, caída). La convergencia se logra con:
  idempotencia (misma clave natural y `tx-<id>` hacia la pasarela, TKY-88), timeouts y reintentos
  (TKY-85), circuit breaker (TKY-86), compensaciones (reintegro) y la reconciliación.
- **ms-tickets → ms-notificaciones:** el mail llega después de la compra, por RabbitMQ, con un
  `mensajeId` derivado de la reserva para no duplicarlo.
- **ms-eventos ↔ ms-tickets:** la disponibilidad se crea a partir del evento publicado; los
  cambios de estado del evento llegan por endpoints internos.

## Límites conocidos

- Los `REINTEGRO_PENDIENTE` no se reintentan solos: hay que reconstruir qué débitos falta
  reintegrar y se resuelven a mano.
- Si falla la publicación de la notificación (broker caído), el aviso se pierde: no hay outbox.
- Una reserva puede quedar en `PENDIENTE_PAGO` mientras ms-transacciones no responda o el débito
  siga `PENDIENTE`; los lugares quedan tomados hasta que se resuelva.
