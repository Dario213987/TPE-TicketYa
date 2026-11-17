# ms-preferencias — preferencias del cliente (US-09.01)

Guarda los gustos declarados por el cliente: tipos de evento favoritos, artistas favoritos y ciudad.
Requiere los encabezados `X-User-Id` (numérico) y `X-User-Role: CLIENTE`: sin ellos responde `401` y con
otro rol `403`. El gateway expone las rutas bajo `/api/preferencias`.

El cliente se toma siempre de `X-User-Id`, nunca del path ni del cuerpo, así que un cliente no puede
leer ni pisar las preferencias de otro. El contrato está en
`contracts/preferencias-contract/src/main/resources/openapi.yaml`.

## `GET /api/preferencias/mias`

Devuelve las preferencias actuales. Si el cliente todavía no cargó nada responde `200` con las listas
vacías (no `404`) y no crea el documento.

```json
{
  "clienteId": 7,
  "tiposFavoritos": ["RECITAL", "TEATRO"],
  "artistasFavoritos": [15, 20],
  "ciudad": "Tandil"
}
```

Sin datos cargados:

```json
{
  "clienteId": 7,
  "tiposFavoritos": [],
  "artistasFavoritos": [],
  "ciudad": null
}
```

`ciudad` queda en `null` hasta que se integre el perfil de `ms-usuarios` (ver [Pendientes](#pendientes)).

## `PUT /api/preferencias/mias`

Reemplaza el documento completo: lo que se envía sustituye a lo anterior, no se mergea.

### Request

```json
{
  "tiposFavoritos": ["RECITAL"],
  "artistasFavoritos": [15],
  "ciudad": "Tandil"
}
```

### `200 OK`

Devuelve las preferencias guardadas, con el mismo formato que el `GET`.

### Reglas

| Campo | Regla |
|---|---|
| `ciudad` | Obligatoria. Entre 1 y 100 caracteres y al menos un carácter no blanco. Se guarda con `trim`. |
| `tiposFavoritos` | Hasta 20 valores del enum `TipoEvento` (`RECITAL`, `FESTIVAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `OTRO`), el mismo enum del contrato de `ms-eventos`. |
| `artistasFavoritos` | Hasta 100 ids de participante, todos mayores a 0. |

- Las listas se deduplican conservando el orden de llegada.
- Enviar `[]` borra los favoritos guardados; omitir el campo se interpreta igual que `[]`.
- Un tipo de evento fuera del enum responde `400`.

### Errores

- `400`: ciudad vacía o solo espacios, tipo de evento desconocido, lista o id fuera de rango.
- `401`: faltan `X-User-Id` o `X-User-Role`.
- `403`: el rol no es `CLIENTE`.

## Modelo documental (MongoDB)

Colección `preferencias` en la base `ticketya_preferencias`, un documento por cliente con el id del
cliente como `_id`:

```json
{
  "_id": 7,
  "tiposFavoritos": ["RECITAL", "TEATRO"],
  "artistasFavoritos": [15, 20],
  "ciudad": "Tandil",
  "_class": "org.tudai.ticketya.preferencias.domain.Preferencia"
}
```

Por qué documental y no relacional:

- El dato se lee y se escribe entero: las preferencias de un cliente se piden juntas y se reemplazan
  juntas, sin consultas parciales por campo. Un acceso equivale a un documento.
- No hay relaciones que mantener: los favoritos son listas embebidas dentro del mismo cliente, no filas
  en tablas asociativas ni joins.
- El `_id` natural es el id del cliente, así que `save` reemplaza el documento anterior sin índices
  adicionales y sin riesgo de duplicados.
- El esquema va a crecer con el historial de compras y la co-ocurrencia (US-09.02 a US-09.04), que son
  estructuras anidadas y variables; en documental eso es un documento más grande, en relacional son
  tablas nuevas.

La comparación con las alternativas está en
[00-arquitectura](../specs/00-arquitectura.md#ms-preferencias-mongodb-entrega-2).

## Configuración

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `0` (puerto asignado) |
| `spring.data.mongodb.uri` | `MONGODB_URI` | `mongodb://localhost:27017/ticketya_preferencias` |
| `eureka.client.service-url.defaultZone` | `EUREKA_URL` | `http://localhost:8761/eureka` |

En local, `infra/docker-compose.yml` levanta `db-preferencias` en `127.0.0.1:5437` con la base
`ticketya_preferencias`. Los tests de integración usan MongoDB en Testcontainers y se omiten solos si no
hay Docker.

## Historial de compras y asistencias (US-09.02)

El historial lo escribe **únicamente** el consumidor de eventos; el cliente solo lo lee. Se guarda
embebido en el mismo documento `preferencias`, en el campo `historial`, y el `PUT` de preferencias no lo
toca.

### Endpoint

`GET /api/preferencias/historial` (rol `CLIENTE`) devuelve el historial del cliente autenticado. Si
todavía no compró nada responde `200` con `[]`.

```json
[
  {
    "eventoId": 5,
    "tipo": "RECITAL",
    "participanteIds": [15, 20],
    "ciudad": "Tandil",
    "fecha": "2026-12-01T20:00:00",
    "comprado": true,
    "usado": true
  }
]
```

La `fecha` del historial se devuelve en UTC, sin offset.

### Eventos que lo alimentan

| Stream | Lo publica | Cuándo | Qué hace ms-preferencias |
|---|---|---|---|
| `venta.pagada` | ms-tickets | compra confirmada | suma el evento al historial (`comprado = true`) con tipo, participantes, ciudad y fecha |
| `ticket.utilizado` | ms-tickets | ingreso validado | marca el evento como usado (`usado = true`); el peso mayor lo aplica la recomendación (US-13.03) |

#### `venta.pagada`

| Campo | Obligatorio | Tipo | Descripción |
|---|---|---|---|
| `clienteId` | sí | long | cliente que compró |
| `eventoId` | sí | long | evento comprado |
| `tipo` | no | `TipoEvento` | `RECITAL`, `FESTIVAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `OTRO` |
| `participanteIds` | no | CSV de longs | ids de participantes del evento |
| `ciudad` | no | string | ciudad del evento |
| `fechaEvento` | no | ISO-8601 | fecha y hora del evento (`2026-12-01T20:00:00Z`) |
| `traceId` | no | string | correlación de la operación que originó el evento |

#### `ticket.utilizado`

| Campo | Obligatorio | Tipo | Descripción |
|---|---|---|---|
| `clienteId` | sí | long | cliente que ingresó |
| `eventoId` | sí | long | evento en el que se usó la entrada |
| `ticketId` | no | long | id de la entrada |
| `fechaUso` | no | ISO-8601 | momento del ingreso |
| `traceId` | no | string | correlación de la operación |

### Reglas de procesamiento

- **Upsert por evento**: una compra del mismo `eventoId` actualiza la entrada existente, no la duplica.
  Un uso solo prende `usado`; si llega antes que la compra, crea la entrada y la compra posterior
  completa tipo, participantes, ciudad y fecha.
- **Idempotencia por id de mensaje**: se guarda una clave
  `preferencias:historial:procesados:<stream>:<id>` en Redis (TTL 7 días) y un mensaje repetido se
  ignora. La segunda capa (upsert por evento) cubre el caso de un mensaje republicado con otro id.
- **Mensajes inválidos** (falta un campo obligatorio, tipo o fecha inválidos): se registran en el log y
  se descartan para no bloquear el stream. Los mensajes válidos se confirman automáticamente.
- **Consumidor**: grupo `ms-preferencias`, consumidor `ms-preferencias-1`, lectura desde el último
  mensaje entregado. Si el servicio está caído, el stream conserva los mensajes y se procesan al
  volver.
- **Límites conocidos**: no hay reintento de un mensaje que falla al procesarse (se loguea y se
  confirma); hay un solo consumidor, así que no se garantiza orden entre los dos streams. La
  co-ocurrencia (`co_compras`) se calcula en US-13.03 y el endpoint de sugerencias es US-13.04.

### Configuración

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` |
| `spring.data.redis.port` | `REDIS_PORT` | `6381` |
| `preferencias.historial.consumer-enabled` | `PREFERENCIAS_HISTORIAL_CONSUMER_ENABLED` | `true` |
| `preferencias.historial.stream-venta-pagada` | `PREFERENCIAS_STREAM_VENTA_PAGADA` | `venta.pagada` |
| `preferencias.historial.stream-ticket-utilizado` | `PREFERENCIAS_STREAM_TICKET_UTILIZADO` | `ticket.utilizado` |
| `preferencias.historial.grupo` | `PREFERENCIAS_HISTORIAL_GRUPO` | `ms-preferencias` |
| `preferencias.historial.consumidor` | `PREFERENCIAS_HISTORIAL_CONSUMIDOR` | `ms-preferencias-1` |
| `preferencias.historial.dedupe-ttl` | `PREFERENCIAS_HISTORIAL_DEDUPE_TTL` | `PT168H` (7 días) |
| `preferencias.historial.poll-timeout` | `PREFERENCIAS_HISTORIAL_POLL_TIMEOUT` | `PT1S` |

`infra/docker-compose.yml` levanta `redis-preferencias` en `127.0.0.1:6381`. Con
`PREFERENCIAS_HISTORIAL_CONSUMER_ENABLED=false` el servicio arranca sin consumir (lo usan los tests que
no levantan Redis).

### Cómo probarlo sin los productores

Los productores de `venta.pagada` y `ticket.utilizado` todavía no existen en ms-tickets (D8); para
probar el consumidor se publican los eventos a mano:

```powershell
docker compose -f infra/docker-compose.yml up -d redis-preferencias

# compra confirmada
docker exec -it ticketya-infra-redis-preferencias-1 redis-cli XADD venta.pagada * `
  clienteId 7 eventoId 5 tipo RECITAL participanteIds "15,20" ciudad Tandil `
  fechaEvento 2026-12-01T20:00:00Z traceId trace-1

# uso de la entrada
docker exec -it ticketya-infra-redis-preferencias-1 redis-cli XADD ticket.utilizado * `
  clienteId 7 eventoId 5 ticketId 99 fechaUso 2026-12-02T21:00:00Z traceId trace-2

# consulta
curl.exe -H "X-User-Id: 7" -H "X-User-Role: CLIENTE" http://localhost:8080/api/preferencias/historial
```

Repetir el `XADD` de `venta.pagada` no duplica la entrada: el primer mensaje se procesa y el segundo
actualiza el mismo evento (o se ignora si es el mismo id de mensaje). El test de integración
`HistorialStreamIntegrationTest` hace esto mismo con Redis y MongoDB en Testcontainers.

## Recomendaciones simplificadas (US-09.03)

Esta sección cubre el **algoritmo y la co-ocurrencia**; el endpoint `GET /api/recomendaciones`
(con el cliente de `ms-eventos` y el fallback) es la US-09.04. Acá se explica cómo se calcula el
puntaje de un evento candidato para que la recomendación sea simple y justificable.

### Co-ocurrencia de compras (`co_compras`)

Un documento por evento con los eventos que compraron sus mismos clientes y cuántos clientes compraron
ambos:

```json
{
  "_id": 5,
  "relacionados": [
    { "eventoId": 6, "cantidad": 2 },
    { "eventoId": 9, "cantidad": 1 }
  ]
}
```

Reglas:

- Se actualiza cuando llega un `venta.pagada` de un evento **nuevo** para el cliente (la entrada no
  estaba `comprado`). Una segunda compra del mismo evento no vuelve a contar.
- Por cada evento `X` que el cliente ya había comprado se incrementa el par `(E, X)` y `(X, E)`, así
  la consulta funciona en las dos direcciones.
- La `cantidad` cuenta **clientes**, no compras: si dos clientes compraron 5 y 6, `cantidad` es 2.
- La actualización va después de guardar el historial. Si falla, se registra un warning y el mensaje
  se confirma igual; no se pierde el historial. La deduplicación por id de mensaje (US-09.02) evita
  procesar dos veces el mismo evento.
- La actualización es read-modify-write sobre la lista porque el modelo documentado es
  `relacionados[{eventoId, cantidad}]`. El consumidor procesa los mensajes de forma secuencial; con
  varias instancias de `ms-preferencias` habría que pasar a un `$inc` atómico o a un lock por evento.

### Puntaje

El puntaje es **lineal**, sin aprendizaje automático. Se suman los criterios que coinciden:

| Criterio | Peso | Cuándo suma |
|---|---|---|
| **Artista** | **+5** | un participante del candidato está en `artistasFavoritos`, o participó de un evento del historial con `usado = true` (al que el cliente asistió) |
| **Tipo** | **+3** | el tipo está en `tiposFavoritos`, o aparece al menos una vez en el historial (comprado o asistido) |
| **Co-ocurrencia** | **+1 a +3** | `min(3, cantidad)` de clientes que compraron alguno de los eventos del historial junto con el candidato |
| **Ciudad** | **+1** | la ciudad del candidato coincide con la del cliente (sin distinguir mayúsculas ni espacios) |

Reglas adicionales:

- Se **excluyen** los eventos para los que el cliente ya tiene entradas (historial con `comprado` o
  `usado`), así no se recomienda algo que ya compró.
- Se ordena por **puntaje descendente**, luego por **fecha ascendente** y luego por **id**.
- El endpoint devuelve hasta 10 eventos; el servicio recibe el límite.
- `motivo` es el criterio de mayor peso que explica la sugerencia, con esta prioridad:
  `ARTISTA` (5) > `TIPO` (3) > `CO_COMPRA` (hasta 3) > `CIUDAD` (1). Si empatan `TIPO` y `CO_COMPRA`,
  queda `TIPO`.
- Si el cliente **no tiene historial**, se puntúa solo con `tiposFavoritos`, `artistasFavoritos` y
  `ciudad`. Si tampoco tiene preferencias, no hay coincidencias y la lista queda vacía: el fallback de
  "próximos eventos de mi ciudad" y el `200` con lista vacía cuando `ms-eventos` está caído son parte
  del endpoint (US-09.04).

### Simplificaciones documentadas

- No hay motor externo ni aprendizaje automático: es una suma de coincidencias con pesos fijos.
- No hay recomendación por cercanía geográfica; solo se compara la ciudad declarada.
- La co-ocurrencia se precalcula con las compras (`venta.pagada`); el uso (`ticket.utilizado`) solo
  pondera el historial para el criterio de artista asistido, no cambia los pares.
- Los pares cuentan clientes únicos por evento, no la cantidad de entradas ni de compras repetidas.
- No se explica cada recomendación más allá del `motivo` principal; el detalle completo queda en el
  informe tecnológico.
- Los candidatos (eventos publicados y futuros) los arma el endpoint de US-09.04 con `ms-eventos`; el
  algoritmo de esta card solo puntúa los que recibe.

## Pendientes

- **Artista inexistente**: la spec pide `400` cuando el `participanteId` no existe en `ms-eventos`.
  Falta un endpoint de participantes en `ms-eventos` y un cliente Feign; hoy los ids se guardan sin
  validar contra el servicio.
- **Ciudad por defecto**: la spec pide devolver la ciudad del perfil cuando no hay preferencias
  cargadas. Depende de `ms-usuarios` (US-01.01), que todavía no existe en el repo.
