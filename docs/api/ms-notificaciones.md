# ms-notificaciones

Servicio de **notificaciones por email**. Centraliza el envío: consume mensajes desde RabbitMQ, obtiene datos de evento y usuario, renderiza la plantilla según el motivo y envía el correo, persistiendo el resultado.

## Mensaje (`NotificacionEntranteDTO`, contrato compartido)

- `mensajeId` (obligatorio): clave de idempotencia generada por el productor. Puede ser único con formato `PREFIJO-<epochMs>-<uuid>` (ej. `MST-1731700000000-3f2a9c1e-...`), derivado por motivo con formato `PREFIJO-MOTIVO-eventoId` (`MensajeIdNotificacion.generarDerivado`, usado por `ms-eventos` en `PUBLICACION_ARTISTA` y `CANCELACION`) o derivado por entidad con formato `PREFIJO-ENTIDAD-id` (ej. `MST-RESERVA-99`, usado por `ms-tickets` en `CONFIRMACION_COMPRA`). `EDICION` usa id único por cambio. La idempotencia se resuelve por la combinación `(mensajeId, destinatarioId)`.
- `motivo`: `CANCELACION`, `CONFIRMACION_COMPRA`, `PUBLICACION_ARTISTA`, `EDICION`, `SUSPENSION`.
- `eventoId`, `destinatarioId` (`Long`).
- `destinatarioId` es obligatorio para `CONFIRMACION_COMPRA` (validado por `validarDestinatario()`) y puede ser `null` en avisos masivos. Si es `null`, `NotificacionService` delega en un `DestinatariosResolver` según el motivo y crea una notificación por destinatario. Sin destinatarios no se envía nada. `CANCELACION`, `EDICION` y `SUSPENSION` consultan los compradores del evento en `ms-tickets` (`GET /api/tickets/internal/eventos/{eventoId}/compradores`). `PUBLICACION_ARTISTA` obtiene `lugar.ciudad` y `participantes[].id` desde `ms-eventos` (`GET /api/eventos/{eventoId}`) y consulta `ms-preferencias` (`GET /api/preferencias/internal/destinatarios`) para quedarse con los clientes que tienen algun participante como favorito y declararon la misma ciudad.
- `detalle` (opcional): detalle polimórfico del mensaje. El discriminante es `tipo` y por compatibilidad se acepta el nombre anterior `detalleCompra` (`@JsonAlias`).
- Para `CONFIRMACION_COMPRA`, `detalle` es `DetalleConfirmacionCompra`: `sectorId`, `asientos` (`[{fila, numero}]`, vacío para no numerados), `cantidad`, `monto` (nullable), `moneda`. **No se persiste** en `Notificacion`; viaja en el mensaje y se usa solo para renderizar el mail.
- Las colas y el exchange se centralizan en `ColasNotificacion`: `enviar`, `enviar.dlx` y `enviar.dlq`.

## Flujo

1. **Consumo**: `NotificacionListener` recibe `NotificacionEntranteDTO` desde la cola `enviar`.
2. **Idempotencia individual**: se busca la notificación por `(mensajeId, destinatarioId)`. Si ya está `ENVIADO`, `ENVIANDO` o `NO_ENTREGABLE`, se descarta con `warn`; si está `PENDIENTE` o `FALLIDO`, se reintenta. La restricción única compuesta protege ante consumidores concurrentes.
3. **Expansión masiva**: para un mensaje sin `destinatarioId`, primero se buscan filas existentes por `mensajeId`; si ya existen, se reutilizan sin volver a consultar servicios externos. Si no existen, `DestinatariosResolverRegistry` elige la estrategia por motivo, se descartan ids nulos y duplicados, y todas las notificaciones `PENDIENTE` se persisten en una única transacción antes de enviar emails. Si el servicio externo falla, no queda ninguna fila parcial. Sin destinatarios: no hay filas ni envíos.
4. **Enriquecimiento (Feign, al procesar)**: obtiene evento (`EventoClient` → `ms-eventos`: nombre, fecha, lugar, `lugar.ciudad`, `participantes` y sectores para resolver el nombre del sector por `sectorId`) y usuario (`UsuarioClient` → `ms-usuarios`: nombre, email). Para `PUBLICACION_ARTISTA`, `PreferenciasClient` completa la consulta a `ms-preferencias`; los ids se envían en lotes de hasta 100 y se unifican. El `detalle` se toma del mensaje.
5. **Plantilla**: `PlantillaEmailService` (Thymeleaf) selecciona la estrategia por motivo y resuelve `mail/{MOTIVO}.html`. Para `CONFIRMACION_COMPRA` usa nombre/fecha/lugar del evento, nombre de usuario, sector, asientos (`fila-numero, ...` o `General x N` si no numerado) y monto (tolerando `null`). Para `PUBLICACION_ARTISTA` usa nombre/fecha/lugar/ciudad del evento y la lista de participantes.
6. **Persistencia y envío**: por cada destinatario se pasa `PENDIENTE` → `ENVIANDO` → `EmailService` envía el `MimeMessage` HTML por SMTP (MailHog en dev) → `ENVIADO` con `fechaEnvio`. Si un destinatario falla, se marca `FALLIDO`, se continúa con el resto y al final se lanza una excepción agregada para activar el reintento de Rabbit. Un usuario sin email se marca `NO_ENTREGABLE`.

> Nota: si el correo se envió pero falló la actualización a `ENVIADO`, el reintento encontrará el registro en `ENVIANDO` y no se reenviará (prioridad: evitar duplicados). Los avisos masivos reutilizan las filas ya creadas y omiten los destinatarios en estados terminales.

## Patrón Strategy para plantillas

La selección de plantilla se resuelve con el patrón **Strategy** para evitar un `switch` creciente por motivo:

| Componente | Rol |
|---|---|
| `PlantillaNotificacion` | Interfaz de estrategia: declara `motivos()`, `asunto(DatosPlantilla)` y `completar(Context, DatosPlantilla)`. |
| `ConfirmacionCompraPlantilla` | Estrategia de `CONFIRMACION_COMPRA`; resuelve sector, asientos, cantidad, monto y moneda. |
| `AvisoGeneralPlantilla` | Estrategia de `CANCELACION`, `EDICION` y `SUSPENSION`; limpia las variables de compra. |
| `PublicacionArtistaPlantilla` | Estrategia de `PUBLICACION_ARTISTA`; limpia las variables de compra y carga `eventoCiudad` y `participantesTexto`. |
| `DatosPlantilla` | Contexto común: motivo, evento, usuario y detalle polimórfico. |
| `PlantillaEmailService` | Contexto del patrón: registra las estrategias en un `Map<MotivoNotificacion, PlantillaNotificacion>` y delega asunto/renderizado. |

`PlantillaEmailService` recibe todas las estrategias como beans de Spring y, al arrancar:

1. Registra cada motivo en el mapa.
2. Falla si dos estrategias declaran el mismo motivo.
3. Falla si algún valor de `MotivoNotificacion` no tiene estrategia.

Para agregar un motivo nuevo alcanza con implementar `PlantillaNotificacion`, declarar el motivo en `motivos()`, anotar la clase con `@Component` y crear `templates/mail/{MOTIVO}.html`. No hace falta modificar `PlantillaEmailService`.

Los templates comparten el fragmento `templates/mail/fragments/layout.html` con el encabezado, los datos del evento y el pie. Cada template usa `th:replace` para el layout y solo define el contenido específico del motivo.

## Patrón Strategy para destinatarios

La resolución de destinatarios para avisos masivos usa el mismo criterio: `NotificacionService` no conoce servicios externos por motivo, sino que delega en `DestinatariosResolverRegistry`.

| Componente | Rol |
|---|---|
| `DestinatariosResolver` | Interfaz de estrategia: declara `motivos()` y `resolver(NotificacionEntranteDTO)`. |
| `CompradoresEventoResolver` | Estrategia de `CANCELACION`, `EDICION` y `SUSPENSION`; consulta compradores a `ms-tickets`. |
| `PreferenciasArtistaResolver` | Estrategia de `PUBLICACION_ARTISTA`; obtiene ciudad y participantes del evento y cruza con favoritos de `ms-preferencias`. |
| `DestinatariosResolverRegistry` | Contexto del patrón: valida que no haya dos resolvers para un mismo motivo y que los motivos masivos estén cubiertos. |
| `NotificacionService` | Consulta el registry sólo cuando `destinatarioId` es `null`; `CONFIRMACION_COMPRA` sigue siendo individual. |

`PreferenciasArtistaResolver` falla con `IllegalStateException` si el evento no tiene `lugar.ciudad`, y envía los ids de participantes en lotes de 100 porque ese es el `maxItems` del contrato. La unión de respuestas se ordena y deduplica antes de persistir.

## Runbook E2E: `PUBLICACION_ARTISTA` con MailHog

Requisitos: `infra/docker-compose.yml` levantado, `ms-eventos`, `ms-preferencias`, `ms-usuarios` y `ms-notificaciones` disponibles, y MailHog en `127.0.0.1:1025` (SMTP) / `127.0.0.1:8025` (UI). La colección `postman/ms-notificaciones/ms-notificaciones.postman_collection.json` tiene estos pasos con variables.

Variables sugeridas: `baseUrl=http://localhost:8080`, `rabbitmqUrl=http://localhost:15672`, `mailhogUrl=http://localhost:8025`, `clienteId`, `artistaId`, `artistaNombre`, `eventoId`, `ciudad`, `mensajeId=MSE-PUBLICACION_ARTISTA-{{eventoId}}`. Rabbit usa las credenciales de `infra/.env` (`RABBITMQ_USER` / `RABBITMQ_PASSWORD`).

1. Elegir un evento visible con `GET {{baseUrl}}/api/eventos/{{eventoId}}` y confirmar que `lugar.ciudad` es `{{ciudad}}` y que `participantes[].id` incluye `{{artistaId}}`.
2. Cargar preferencias con `PUT {{baseUrl}}/api/preferencias/mias`, headers `X-User-Id: {{clienteId}}` y `X-User-Role: CLIENTE`, body:
   ```json
   { "ciudad": "{{ciudad}}", "tiposFavoritos": [], "artistasFavoritos": [{{artistaId}}] }
   ```
3. Opcional: verificar el contrato TKY-105 contra la URL directa de `ms-preferencias` (el gateway bloquea `/api/preferencias/internal/**`): `GET /api/preferencias/internal/destinatarios?ciudad={{ciudad}}&participanteIds={{artistaId}}` debe devolver `{{clienteId}}`.
4. Limpiar MailHog: `DELETE {{mailhogUrl}}/api/v1/messages`.
5. Publicar el aviso en la cola `enviar` con la Management API de Rabbit: `POST {{rabbitmqUrl}}/api/exchanges/%2F/amq.default/publish`, Basic Auth y body:
   ```json
   {
     "properties": {
       "delivery_mode": 2,
       "content_type": "application/json",
       "headers": {
         "__TypeId__": "org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO"
       }
     },
     "routing_key": "enviar",
     "payload": "{\"mensajeId\":\"{{mensajeId}}\",\"motivo\":\"PUBLICACION_ARTISTA\",\"eventoId\":{{eventoId}},\"destinatarioId\":null}",
     "payload_encoding": "string"
   }
   ```
   El header `__TypeId__` es obligatorio para que `Jackson2JsonMessageConverter` hidrate `NotificacionEntranteDTO`.
6. Consultar `GET {{mailhogUrl}}/api/v2/messages`: debe haber 1 mensaje, con `PUBLICACION_ARTISTA` en el asunto y `{{artistaNombre}}`, `{{ciudad}}` y el nombre del evento en el cuerpo.
7. Republicar exactamente el mismo body del paso 5 y repetir el paso 6: no debe aparecer un segundo mail (misma clave `(mensaje_id, destinatario_id)`).
8. Si no llega el correo, revisar logs de `ms-notificaciones`, el estado de la cola `enviar` y la DLQ `enviar.dlq`. Un fallo de `ms-preferencias` ocurre antes de persistir: Rabbit reintenta 3 veces y termina en DLQ sin filas parciales.

## Reintentos y cola de fallidos

- Listener con **3 intentos** y backoff exponencial **1s → 2s → 4s** (`RetryOperationsInterceptor`), `default-requeue-rejected=false`.
- En avisos masivos, un fallo por destinatario no aborta el resto: las filas exitosas quedan `ENVIADO`, las fallidas `FALLIDO` y el mensaje se reintenta. En el reintento se reutilizan las filas existentes y se omiten `ENVIADO`, `ENVIANDO` y `NO_ENTREGABLE`.
- Al agotar reintentos el mensaje se rechaza y el broker lo enruta por **DLX** `enviar.dlx` a la **DLQ** `enviar.dlq`. Las filas pueden quedar `FALLIDO`; la reprocesación/gestión de la DLQ es operativa.

## Tecnologías y por qué

| Tecnología | Uso / motivo |
|---|---|
| Spring AMQP + RabbitMQ | Mensajería asíncrona y desacoplada entre servicios; soporte nativo de colas, reintentos y DLQ. |
| Spring Mail (JavaMailSender) + MailHog | Envío SMTP estándar; MailHog como servidor de pruebas en desarrollo (inspección sin correos reales). |
| Thymeleaf | Plantillas HTML por motivo, separando presentación de datos. |

