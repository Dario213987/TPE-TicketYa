# Qué NO se cachea (y por qué)

TicketYa cachea solo datos de catálogo de `ms-eventos` (ver [Cache de ms-eventos](api/ms-eventos-cache.md)).
La disponibilidad de asientos, las reservas, el estado de pago y los tickets **no se cachean**:
cambian todo el tiempo y un dato viejo puede causar una **doble venta** o mostrarle al cliente
**información falsa**. Este documento justifica cada exclusión (TKY-84).

## Criterio

Un dato se cachea solo si cumple las tres condiciones:

1. **Se lee mucho más de lo que se escribe.**
2. **Cambia poco.**
3. **Verlo unos segundos desactualizado no causa daño**: no habilita una venta que no corresponde
   ni le informa al cliente algo que no es cierto sobre su compra.

Si un dato viejo puede vender dos veces lo mismo o mentirle al cliente sobre su dinero o su entrada,
**no se cachea**: se lee siempre de la base, que es la fuente de verdad.

## Resumen

| Dato | Servicio | ¿Se cachea? | Por qué |
|---|---|---|---|
| Detalle de evento | ms-eventos | **Sí**, 5 min, se invalida al modificarlo (TKY-82) | Se lee mucho, cambia poco; cada cambio lo invalida |
| Búsquedas del listado | ms-eventos | **Sí**, 30 s, sin invalidar (TKY-83) | Un listado 30 s viejo no vende nada: la compra valida contra la base |
| Disponibilidad de asientos y sectores | ms-tickets | **No** | Doble venta o venta perdida |
| Reservas | ms-tickets | **No** | Pagar una reserva vencida; tiempo restante falso |
| Estado de pago | ms-tickets / ms-transacciones | **No** | Información falsa sobre el dinero del cliente; rompe la reconciliación |
| Tickets | ms-tickets | **No** | Entradas inválidas que figuran válidas, o al revés |

## Disponibilidad de asientos y sectores

**Qué es y dónde vive:** cuántos lugares quedan en cada sector y el estado de cada asiento
(`LIBRE`, `RESERVADO`, `VENDIDO`). Tablas `disponibilidad_eventos`, `disponibilidad_sectores` y
`disponibilidad_asientos` de ms-tickets.

**Por qué no se cachea:** cambia con cada reserva, cada vencimiento y cada compra, justo en los
momentos de más demanda (cuando más se lee). Con un dato viejo:

- un asiento ya reservado o vendido figura **libre** y dos clientes intentan quedarse con él:
  **doble venta** si la decisión se tomara con ese dato;
- al revés, un asiento que se liberó (reserva vencida o devolución) figura **ocupado** y se pierde
  la venta;
- el cupo de un sector no numerado figura con lugares cuando ya se agotó.

**Cómo se lee en cambio:** siempre de la base. Al reservar, las filas de disponibilidad del evento y
del sector se leen con lock `PESSIMISTIC_WRITE` (`DisponibilidadEventoRepository.findByIdForUpdate`,
`DisponibilidadSectorRepository`), así dos reservas simultáneas del mismo lugar se ordenan y la
segunda ve el dato ya actualizado.

## Reservas

**Qué es y dónde vive:** la reserva temporal de un cliente mientras paga, con su vencimiento
(`expiraEl`). Tabla `reservas` de ms-tickets.

**Por qué no se cachea:** su estado cambia por el paso del tiempo (vence sola) y por acciones del
cliente, del pago y de los jobs. Con un dato viejo:

- una reserva **vencida** figura activa y se intenta pagar algo cuyos lugares ya se liberaron y
  pueden estar en manos de otro cliente;
- la cuenta regresiva que ve el cliente es falsa (cree que le queda tiempo y no);
- una reserva ya pagada figura pendiente y el cliente paga dos veces.

**Cómo se lee en cambio:** siempre de la base; para pagarla se lee con lock `PESSIMISTIC_WRITE`
(`ReservaRepository.findByIdForUpdate`) y antes de cobrar se liberan las reservas vencidas del
evento, así una reserva vencida responde `409` en lugar de cobrarse.

## Estado de pago

**Qué es y dónde vive:** si el cobro está `PENDIENTE_PAGO`, `APROBADO`, `RECHAZADO`, `REINTEGRADO`
o `REINTEGRO_PENDIENTE`. Campo `estado_pago` de `reservas` (ms-tickets) y tabla `transacciones`
(ms-transacciones).

**Por qué no se cachea:** es dinero del cliente y cambia en segundos (respuesta de la pasarela,
reintentos, compensaciones, reconciliación). Con un dato viejo:

- el cliente ve **"pendiente" cuando ya se le cobró**, o **"aprobado" cuando se rechazó**:
  información falsa sobre su dinero;
- un reintento del pago podría decidir con un estado viejo y **cobrar dos veces**; la clave de
  idempotencia (TKY-88) funciona porque compara contra el estado guardado en la base;
- la reconciliación de pagos pendientes (TKY-87) decide emitir, rechazar o reintegrar según el
  estado real; con un dato viejo emitiría entradas no cobradas o reintegraría un pago válido.

**Cómo se lee en cambio:** siempre de la base de cada servicio; ms-tickets consulta a
ms-transacciones cuando necesita el estado del cobro.

## Tickets

**Qué es y dónde vive:** cada entrada con su estado (`RESERVADO`, `PENDIENTE_PAGO`, `EMITIDO`,
`VENDIDO`, `USADO`, `DEVUELTO`, `CANCELADO`, `EXPIRADO`, `LIBERADO`) y su código QR. Tabla `tickets`
de ms-tickets.

**Por qué no se cachea:** es lo que el cliente usa para entrar y cambia por compra, devolución,
cancelación del evento o uso. Con un dato viejo:

- un ticket **devuelto o cancelado** (por ejemplo, porque se canceló el evento) figura válido:
  el cliente cree que tiene entrada y no la tiene;
- un ticket **ya usado** figura sin usar y el mismo QR podría servir dos veces;
- un ticket recién comprado no aparece en "mis tickets" y el cliente cree que la compra falló.

**Cómo se lee en cambio:** siempre de la base; los cambios de estado que compiten (vencimiento de
reservados) se hacen con lock `PESSIMISTIC_WRITE` (`TicketRepository`).

## Aclaración: el Redis de ms-tickets no es cache

ms-tickets usa Redis (`redis-tickets`) para la **fila virtual** y los **pases** de eventos de alta
demanda ([fila virtual](api/ms-tickets-fila.md)). Ahí Redis es **el lugar donde se guardan** la
fila y los pases (la fuente de verdad de ese dato), no una copia de la base: no hay dato viejo que
pueda diferir. Con un pase vigente, la reserva igual valida la disponibilidad contra la base.
