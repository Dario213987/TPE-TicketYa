# ms-notificaciones

Servicio de **notificaciones por email**. Centraliza el envío: consume mensajes desde RabbitMQ, obtiene datos de evento y usuario, renderiza la plantilla según el motivo y envía el correo, persistiendo el resultado.

## Mensaje (`NotificacionEntranteDTO`, contrato compartido)

- `mensajeId` (obligatorio): clave de idempotencia generada por el productor. Puede ser único con formato `PREFIJO-<epochMs>-<uuid>` (ej. `MST-1731700000000-3f2a9c1e-...`) o derivado y estable con formato `PREFIJO-MOTIVO-eventoId` (`MensajeIdNotificacion.generarDerivado`). `ms-eventos` usa id estable para `CANCELACION` (un evento se cancela una vez) e id único por cambio para `EDICION` (cada reprogramación notifica). La idempotencia se resuelve por la combinación `(mensajeId, destinatarioId)`.
- `motivo`: `CANCELACION`, `CONFIRMACION_COMPRA`, `PUBLICACION_ARTISTA`, `EDICION`, `SUSPENSION`.
- `eventoId`, `destinatarioId` (`Long`).
- `destinatarioId` es obligatorio para `CONFIRMACION_COMPRA` (validado por `validarDestinatario()`) y puede ser `null` en avisos masivos. Si es `null`, `NotificacionService` consulta los compradores del evento en `ms-tickets` (`GET /api/tickets/internal/eventos/{eventoId}/compradores`) y crea una notificación por comprador. Sin compradores no se envía nada. Los avisos masivos actuales son `CANCELACION` (incluye el aviso de reintegro) y `EDICION` (fecha/lugar actualizados).
- `detalle` (opcional): detalle polimórfico del mensaje. El discriminante es `tipo` y por compatibilidad se acepta el nombre anterior `detalleCompra` (`@JsonAlias`).
- Para `CONFIRMACION_COMPRA`, `detalle` es `DetalleConfirmacionCompra`: `sectorId`, `asientos` (`[{fila, numero}]`, vacío para no numerados), `cantidad`, `monto` (nullable), `moneda`. **No se persiste** en `Notificacion`; viaja en el mensaje y se usa solo para renderizar el mail.
- Las colas y el exchange se centralizan en `ColasNotificacion`: `enviar`, `enviar.dlx` y `enviar.dlq`.

## Flujo

1. **Consumo**: `NotificacionListener` recibe `NotificacionEntranteDTO` desde la cola `enviar`.
2. **Idempotencia individual**: se busca la notificación por `(mensajeId, destinatarioId)`. Si ya está `ENVIADO`, `ENVIANDO` o `NO_ENTREGABLE`, se descarta con `warn`; si está `PENDIENTE` o `FALLIDO`, se reintenta. La restricción única compuesta protege ante consumidores concurrentes.
3. **Expansión masiva**: para un mensaje sin `destinatarioId`, primero se buscan filas existentes por `mensajeId`; si ya existen, se reutilizan sin volver a consultar `ms-tickets`. Si no existen, se consultan los compradores, se descartan ids nulos y duplicados, y todas las notificaciones `PENDIENTE` se persisten en una única transacción antes de enviar emails. Si `ms-tickets` falla, no queda ninguna fila parcial. No hay compradores: no hay filas ni envíos.
4. **Enriquecimiento (Feign, al procesar)**: obtiene evento (`EventoClient` → `ms-eventos`: nombre, fecha, lugar y sectores para resolver el nombre del sector por `sectorId`) y usuario (`UsuarioClient` → `ms-usuarios`: nombre, email). El `detalle` se toma del mensaje.
5. **Plantilla**: `PlantillaEmailService` (Thymeleaf) selecciona la estrategia por motivo y resuelve `mail/{MOTIVO}.html`. Para `CONFIRMACION_COMPRA` usa nombre/fecha/lugar del evento, nombre de usuario, sector, asientos (`fila-numero, ...` o `General x N` si no numerado) y monto (tolerando `null`).
6. **Persistencia y envío**: por cada destinatario se pasa `PENDIENTE` → `ENVIANDO` → `EmailService` envía el `MimeMessage` HTML por SMTP (MailHog en dev) → `ENVIADO` con `fechaEnvio`. Si un destinatario falla, se marca `FALLIDO`, se continúa con el resto y al final se lanza una excepción agregada para activar el reintento de Rabbit. Un usuario sin email se marca `NO_ENTREGABLE`.

> Nota: si el correo se envió pero falló la actualización a `ENVIADO`, el reintento encontrará el registro en `ENVIANDO` y no se reenviará (prioridad: evitar duplicados). Los avisos masivos reutilizan las filas ya creadas y omiten los destinatarios en estados terminales.

## Patrón Strategy para plantillas

La selección de plantilla se resuelve con el patrón **Strategy** para evitar un `switch` creciente por motivo:

| Componente | Rol |
|---|---|
| `PlantillaNotificacion` | Interfaz de estrategia: declara `motivos()`, `asunto(DatosPlantilla)` y `completar(Context, DatosPlantilla)`. |
| `ConfirmacionCompraPlantilla` | Estrategia de `CONFIRMACION_COMPRA`; resuelve sector, asientos, cantidad, monto y moneda. |
| `AvisoGeneralPlantilla` | Estrategia de `CANCELACION`, `EDICION`, `PUBLICACION_ARTISTA` y `SUSPENSION`; limpia las variables de compra. |
| `DatosPlantilla` | Contexto común: motivo, evento, usuario y detalle polimórfico. |
| `PlantillaEmailService` | Contexto del patrón: registra las estrategias en un `Map<MotivoNotificacion, PlantillaNotificacion>` y delega asunto/renderizado. |

`PlantillaEmailService` recibe todas las estrategias como beans de Spring y, al arrancar:

1. Registra cada motivo en el mapa.
2. Falla si dos estrategias declaran el mismo motivo.
3. Falla si algún valor de `MotivoNotificacion` no tiene estrategia.

Para agregar un motivo nuevo alcanza con implementar `PlantillaNotificacion`, declarar el motivo en `motivos()`, anotar la clase con `@Component` y crear `templates/mail/{MOTIVO}.html`. No hace falta modificar `PlantillaEmailService`.

Los templates comparten el fragmento `templates/mail/fragments/layout.html` con el encabezado, los datos del evento y el pie. Cada template usa `th:replace` para el layout y solo define el contenido específico del motivo.

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

