# ms-devoluciones: Solicitud de devolución de entradas (US-06.08 / TKY-65)

Microservicio encargado de gestionar las solicitudes de devolución de entradas de los clientes de TicketYa.

## Descripción y reglas de negocio

Como cliente, puedo solicitar la devolución de una entrada comprada hasta 72 horas antes del inicio del evento para recuperar el dinero abonado.

### Criterios de aceptación

1. **Creación exitosa (201 Created):**
   - El ticket debe pertenecer al cliente autenticado (`X-User-Id`).
   - El ticket debe encontrarse en estado `EMITIDO` (o `VENDIDO`) y no haber sido utilizado (`utilizado: false`).
   - El plazo restante para el inicio del evento debe ser mayor o igual a 72 horas (`restante >= 72 hs`, inclusivo).
   - Se crea la solicitud en estado `PENDIENTE`.

2. **Fuera de plazo (409 Conflict):**
   - Si faltan menos de 72 horas para el inicio del evento, se rechaza la solicitud con código `409 Conflict` y detalle explicativo.
   - Si la fecha del evento no puede determinarse, se responde `409 Conflict`.

3. **Ticket no apto o solicitud duplicada (409 Conflict):**
   - Si el ticket ya fue utilizado (`USADO` o `utilizado: true`), responde `409 Conflict`.
   - Si el ticket ya fue devuelto (`DEVUELTO`), responde `409 Conflict`.
   - Si el ticket se encuentra en otro estado no emitido (`RESERVADO`, `CANCELADO`, `EXPIRADO`, `LIBERADO`), responde `409 Conflict`.
   - Si el ticket ya posee otra solicitud en estado `PENDIENTE`, responde `409 Conflict`. Protegido a nivel de base de datos mediante índice único parcial:
     `CREATE UNIQUE INDEX uq_devoluciones_ticket_pendiente ON devoluciones (ticket_id) WHERE estado = 'PENDIENTE';`
   - *Regla de reintento:* Si una solicitud anterior fue `DENEGADA`, el cliente puede volver a solicitar la devolución si aún faltan `>= 72 hs`.

4. **Titularidad y permisos (403 Forbidden):**
   - Si el ticket pertenece a otro cliente, responde `403 Forbidden`.
   - Si el cuerpo incluye `clienteId` y no coincide con el usuario autenticado (`X-User-Id`), responde `403 Forbidden`.
   - Si el usuario no tiene rol `CLIENTE`, responde `403 Forbidden`.

5. **Ticket inexistente (404 Not Found):**
   - Si el `ticketId` no existe en `ms-tickets`, responde `404 Not Found`.

6. **Validación de campos (400 Bad Request):**
   - El campo `motivo` tiene una longitud máxima de 255 caracteres (`maxLength: 255`). Si se excede, responde `400 Bad Request`.

7. **Sin autenticación (401 Unauthorized):**
   - Peticiones sin los encabezados `X-User-Id` y `X-User-Role` responden `401 Unauthorized`.

---

## API Endpoints

### `POST /api/devoluciones`

Crea una solicitud de devolución.

#### Headers requeridos
| Header | Tipo | Descripción |
|---|---|---|
| `X-User-Id` | Long | ID del cliente autenticado |
| `X-User-Role` | String | Rol del usuario (`CLIENTE`) |
| `Content-Type` | String | `application/json` |

#### Request Body
```json
{
  "ticketId": 100,
  "clienteId": 10,
  "motivo": "No podré asistir al evento por motivos personales"
}
```
* `ticketId` (Long, obligatorio): ID del ticket a devolver.
* `clienteId` (Long, opcional): Si se incluye, debe ser idéntico al valor de `X-User-Id`.
* `motivo` (String, opcional, máx 255 caracteres): Razón de la solicitud.

#### Response `201 Created`
```json
{
  "id": 1,
  "clienteId": 10,
  "ticketId": 100,
  "fecha": "2026-11-06T12:00:00Z",
  "estado": "PENDIENTE",
  "transaccionId": 999,
  "motivo": "No podré asistir al evento por motivos personales"
}
```

---

## Integración con otros servicios

* **`ms-tickets`**:
  - Endpoint interno: `GET /api/tickets/internal/entradas/{id}`
  - Proporciona: `clienteId`, `estado`, `utilizado`, `precio`, `eventoId`, `fechaEvento`, `transaccionId`.
* **`ms-eventos`**:
  - Endpoint: `GET /api/eventos/{id}`
  - Utilizado como fallback en caso de que el detalle de la entrada no contenga directamente la fecha y hora del evento. La fecha se deserializa como `LocalDateTime` respetando el horario exacto del evento para el cálculo de las 72 hs.
