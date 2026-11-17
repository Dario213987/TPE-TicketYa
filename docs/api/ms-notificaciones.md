# ms-notificaciones

Servicio de **notificaciones por email**. Centraliza el envío: consume mensajes desde RabbitMQ, obtiene datos de evento y usuario, renderiza la plantilla según el motivo y envía el correo, persistiendo el resultado.

## Mensaje (`NotificacionEntranteDTO`, contrato compartido)

- `mensajeId` (obligatorio): clave de idempotencia generada por el productor. Puede ser único con formato `PREFIJO-<epochMs>-<uuid>` (ej. `MST-1731700000000-3f2a9c1e-...`), derivado por motivo con formato `PREFIJO-MOTIVO-eventoId` (`MensajeIdNotificacion.generarDerivado`, usado por `ms-eventos` en `PUBLICACION_ARTISTA` y `CANCELACION`) o derivado por entidad con formato `PREFIJO-ENTIDAD-id` (ej. `MST-RESERVA-99`, usado por `ms-tickets` en `CONFIRMACION_COMPRA`). `EDICION` usa id único por cambio. La idempotencia se resuelve por la combinación `(mensajeId, destinatarioId)`.
- `motivo`: `CANCELACION`, `CONFIRMACION_COMPRA`, `PUBLICACION_ARTISTA`, `EDICION`, `SUSPENSION`.
- `eventoId`, `destinatarioId` (`Long`).
- `destinatarioId` es obligatorio para `CONFIRMACION_COMPRA` (validado por `validarDestinatario()`) y puede ser `null` en avisos masivos. Si es `null`, `NotificacionService` delega en un `DestinatariosResolver` según el motivo y crea una notificación por destinatario. Sin destinatarios no se envía nada. `CANCELACION` (incluye el aviso de reintegro), `EDICION` (fecha/lugar actualizados) y `SUSPENSION` consultan los compradores del evento en `ms-tickets` (`GET /api/tickets/internal/eventos/{eventoId}/compradores`); esa lista incluye los estados `EMITIDO`, `VENDIDO`, `USADO` y `DEVUELTO`, y `DEVUELTO` se conserva a propósito para que el aviso de reintegro llegue también a quien ya solicitó la devolución. `PUBLICACION_ARTISTA` obtiene `lugar.ciudad` y `participantes[].id` desde `ms-eventos` (`GET /api/eventos/{eventoId}`) y consulta `ms-preferencias` (`GET /api/preferencias/internal/destinatarios`) para quedarse con los clientes que tienen algún participante como favorito y declararon la misma ciudad.
- `detalle` (opcional): detalle polimórfico del mensaje. El discriminante es `tipo` y por compatibilidad se acepta el nombre anterior `detalleCompra` (`@JsonAlias`).
- Para `CONFIRMACION_COMPRA`, `detalle` es `DetalleConfirmacionCompra`: `sectorId`, `asientos` (`[{fila, numero}]`, vacío para no numerados), `cantidad`, `monto` (nullable), `moneda`. **No se persiste** en `Notificacion`; viaja en el mensaje y se usa solo para renderizar el mail.
- Las colas y el exchange se centralizan en `ColasNotificacion`: `enviar`, `enviar.dlx` y `notificaciones.fallidas`.

## Flujo

1. **Consumo**: `NotificacionListener` recibe `NotificacionEntranteDTO` desde la cola `enviar`.
2. **Idempotencia individual**: se busca la notificación por `(mensajeId, destinatarioId)`. Si ya está `ENVIADO`, `ENVIANDO` o `NO_ENTREGABLE`, se descarta con `warn`; si está `PENDIENTE` o `FALLIDO`, se reintenta. La restricción única compuesta protege ante consumidores concurrentes.
3. **Expansión masiva**: para un mensaje sin `destinatarioId`, primero se buscan filas existentes por `mensajeId`; si ya existen, se reutilizan sin volver a consultar servicios externos. Si no existen, `DestinatariosResolverRegistry` elige la estrategia por motivo, se descartan ids nulos y duplicados, y todas las notificaciones `PENDIENTE` se persisten en una única transacción antes de enviar emails. Si el servicio externo falla, no queda ninguna fila parcial. Sin destinatarios: no hay filas ni envíos.
4. **Enriquecimiento (Feign, al procesar)**: obtiene evento (`EventoClient` → `ms-eventos`: nombre, fecha, lugar, `lugar.ciudad`, `participantes` y sectores para resolver el nombre del sector por `sectorId`) y usuario (`UsuarioClient` → `ms-usuarios`: nombre, email). Para `PUBLICACION_ARTISTA`, `PreferenciasClient` completa la consulta a `ms-preferencias`; los ids se envían en lotes de hasta 100 y se unifican. El `detalle` se toma del mensaje.
5. **Plantilla**: `PlantillaEmailService` (Thymeleaf) selecciona la estrategia por motivo y resuelve `mail/{MOTIVO}.html`. Para `CONFIRMACION_COMPRA` usa nombre/fecha/lugar del evento, nombre de usuario, sector, asientos (`fila-numero, ...` o `General x N` si no numerado) y monto (tolerando `null`). Para `PUBLICACION_ARTISTA` usa nombre/fecha/lugar/ciudad del evento y la lista de participantes.
6. **Persistencia y envío**: por cada destinatario se pasa `PENDIENTE` → `ENVIANDO` → `EmailService` envía el `MimeMessage` HTML por SMTP (MailHog en dev) → `ENVIADO` con `fechaEnvio`. Un fallo de conexión deja la fila en `PENDIENTE` con `ultimo_error` y relanza para que Rabbit reintente; un timeout deja la fila en `ENVIANDO` y no relanza para evitar duplicados; un error permanente marca `NO_ENTREGABLE` (dirección inválida) o `FALLIDO` (autenticación/configuración) y no relanza. Un error de plantilla marca `FALLIDO` y no relanza. En avisos masivos, un fallo de conexión corta el lote y relanza; los fallos permanentes no cortan el lote. Un usuario sin email se marca `NO_ENTREGABLE`.

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

## Conexión SMTP con MailHog

`ms-notificaciones` no tiene código específico para MailHog: lo alcanza por SMTP. Spring Boot crea el `JavaMailSender` con `spring.mail.host` y `spring.mail.port` (`ms-notificaciones/src/main/resources/application.properties:26-27`); `EmailService.enviar` arma el `MimeMessage` y llama `mailSender.send(...)` (`EmailService.java:45-51`), invocado desde `NotificacionService.java:209` después de resolver destinatario, evento/usuario y plantilla.

| Escenario | SMTP a usar | Configuración |
| --- | --- | --- |
| Servicio corriendo en la IDE/host | `127.0.0.1:1025` | Default `MAIL_HOST=localhost`, `MAIL_PORT=1025`. |
| Servicio corriendo en Docker | `mailhog:1025` | Setear `MAIL_HOST=mailhog` y compartir la red `ticketya-infra_default` con el compose de `infra`. |

MailHog se levanta con `docker compose -f infra/docker-compose.yml up -d mailhog` y expone SMTP en `127.0.0.1:1025` y UI en `http://localhost:8025` (`infra/docker-compose.yml:181-186`). La bandeja se consulta en la UI o con `GET http://localhost:8025/api/v2/messages`, y se limpia con `DELETE http://localhost:8025/api/v1/messages`. Los correos quedan cautivos en MailHog y no se envían a casillas reales; el estado persistido de la notificación está en `db-notificaciones`.

No confundir puertos: RabbitMQ Management está en `15672` y es donde se inspeccionan `enviar` y `notificaciones.fallidas`; MailHog `8025` es donde se ven los mails. Si no llega un correo, revisar logs de `ms-notificaciones`, la cola `enviar`, la DLQ `notificaciones.fallidas` y los timeouts SMTP configurables (`MAIL_SMTP_CONNECTION_TIMEOUT_MS`, `MAIL_SMTP_TIMEOUT_MS`, `MAIL_SMTP_WRITE_TIMEOUT_MS`).

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
8. Si no llega el correo, revisar logs de `ms-notificaciones`, el estado de la cola `enviar` y la DLQ `notificaciones.fallidas`. Un fallo de `ms-preferencias` ocurre antes de persistir: Rabbit reintenta 8 veces y termina en DLQ sin filas parciales.

## Reintentos y cola de fallidos

- Listener con **8 intentos** y backoff exponencial **5s → 10s → 20s → 40s → 80s → 160s → 300s → 300s** (`RetryOperationsInterceptor`), tope de 5 min, `default-requeue-rejected=false`.
- Configurable por entorno: `REINTENTOS_MAX_INTENTOS`, `REINTENTOS_INTERVALO_INICIAL_MS`, `REINTENTOS_MULTIPLICADOR` y `REINTENTOS_INTERVALO_MAXIMO_MS`. Timeouts SMTP: `MAIL_SMTP_CONNECTION_TIMEOUT_MS`, `MAIL_SMTP_TIMEOUT_MS` y `MAIL_SMTP_WRITE_TIMEOUT_MS` (5000 ms por defecto).
- Clasificación de errores SMTP:
  - `ConnectException`, `UnknownHostException`, `SocketException`, SMTP 4xx y errores no clasificados → transitorios: fila `PENDIENTE`, se relanza.
  - `SocketTimeoutException` o mensaje `timeout`/`timed out` → fila `ENVIANDO`, no se relanza; requiere reconciliación operativa con MailHog antes de reenviar.
  - `AddressException` / SMTP 5xx de dirección → `NO_ENTREGABLE`, no se relanza.
  - Autenticación, parseo, preparación y SMTP 5xx general → `FALLIDO`, no se relanza.
- En avisos masivos, los fallos de conexión cortan el lote y se relanza para reintentar solo las filas `PENDIENTE`; los fallos permanentes se registran y el lote continúa. En el reintento se reutilizan las filas existentes y se omiten `ENVIADO`, `ENVIANDO` y `NO_ENTREGABLE`.
- Al agotar reintentos, `NotificacionDlqRecoverer` extrae `mensajeId` del body, marca como `FALLIDO` (con `ultimo_error`) todas las filas `PENDIENTE` de ese mensaje, loguea en `ERROR` y lanza `AmqpRejectAndDontRequeueException`. El broker enruta por **DLX** `enviar.dlx` a la **DLQ** `notificaciones.fallidas`. Si el body no trae `mensajeId`, solo rechaza y loguea.
- Reproceso operativo: corregir la causa, inspeccionar la DLQ y republicar exactamente el mismo payload en `enviar` (debe mantener el header `__TypeId__` y el mismo `mensajeId`). Las filas `FALLIDO` vuelven a intentarse; las filas `ENVIADO`, `ENVIANDO` y `NO_ENTREGABLE` se omiten.

```bash
# Ver mensajes en la DLQ
curl -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" \
  "http://localhost:15672/api/queues/%2F/notificaciones.fallidas/get" \
  -H 'content-type:application/json' \
  -d '{"count":10,"requeue":false,"encoding":"auto"}'

# Republicar el payload original en la cola enviar
curl -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" \
  "http://localhost:15672/api/exchanges/%2F/amq.default/publish" \
  -H 'content-type:application/json' \
  -d '{"properties":{"delivery_mode":2,"content_type":"application/json","headers":{"__TypeId__":"org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO"}},"routing_key":"enviar","payload":"{\"mensajeId\":\"MST-1-u\",\"motivo\":\"CANCELACION\",\"eventoId\":10,\"destinatarioId\":20}","payload_encoding":"string"}'
```

## Decisiones y resiliencia

- [Justificación del diseño asíncrono](justificacion-diseno-asincrono.md)
- [Runbook de resiliencia](runbook-resiliencia-notificaciones.md)
- [Evidencia de pruebas](../evidencia/resiliencia-notificaciones.md)
- [Publicación no bloqueante](publicacion-no-bloqueante.md)

## Tecnologías y por qué

| Tecnología | Uso / motivo |
|---|---|
| Spring AMQP + RabbitMQ | Mensajería asíncrona y desacoplada entre servicios; soporte nativo de colas, reintentos y DLQ. |
| Spring Mail (JavaMailSender) + MailHog | Envío SMTP estándar; MailHog como servidor de pruebas en desarrollo (inspección sin correos reales). |
| Thymeleaf | Plantillas HTML por motivo, separando presentación de datos. |

