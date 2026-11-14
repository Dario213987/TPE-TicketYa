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

## Pendientes

- **Artista inexistente**: la spec pide `400` cuando el `participanteId` no existe en `ms-eventos`.
  Falta un endpoint de participantes en `ms-eventos` y un cliente Feign; hoy los ids se guardan sin
  validar contra el servicio.
- **Ciudad por defecto**: la spec pide devolver la ciudad del perfil cuando no hay preferencias
  cargadas. Depende de `ms-usuarios` (US-01.01), que todavía no existe en el repo.
