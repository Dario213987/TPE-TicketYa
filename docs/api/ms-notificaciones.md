# ms-notificaciones

Servicio de **notificaciones por email**. Centraliza el envío: consume mensajes desde RabbitMQ, obtiene datos de evento y usuario, renderiza la plantilla según el motivo y envía el correo, persistiendo el resultado.

## Mensaje (`NotificacionEntranteDTO`, contrato compartido)

- `mensajeId` (obligatorio): clave de idempotencia generada por el productor. Formato `PREFIJO-<epochMs>-<uuid>` para IDs únicos por envío (ej. `MST-1731700000000-3f2a9c1e-...`) o `PREFIJO-<ENTIDAD>-<id>` para IDs derivados (ej. `MST-RESERVA-99`, usado por `ms-tickets` en `CONFIRMACION_COMPRA`). Un mismo `mensajeId` se procesa una sola vez.
- `motivo`: `CANCELACION`, `CONFIRMACION_COMPRA`, `PUBLICACION_ARTISTA`, `EDICION`, `SUSPENSION`.
- `eventoId`, `destinatarioId`.
- `detalleCompra` (opcional, solo `CONFIRMACION_COMPRA`): `sectorId`, `asientos` (`[{fila, numero}]`, vacío para no numerados), `cantidad`, `monto` (nullable), `moneda`. **No se persiste** en `Notificacion`; viaja en el mensaje y se usa solo para renderizar el mail.

## Flujo

1. **Consumo**: `NotificacionListener` recibe `NotificacionEntranteDTO` desde la cola `enviar`.
2. **Idempotencia**: se valida `mensajeId`. Se busca/crea la notificación por `mensajeId` (único en BD): si ya está `ENVIADO` o `ENVIANDO`, se descarta registrando `warn` ("Mensaje duplicado descartado: {mensajeId}"); si está `PENDIENTE` (o es nueva) se continúa. Se maneja condición de carrera por restricción única.
3. **Enriquecimiento (Feign, al procesar)**: obtiene evento (`EventoClient` → `ms-eventos`: nombre, fecha, lugar y sectores para resolver el nombre del sector por `sectorId`) y usuario (`UsuarioClient` → `ms-usuarios`: nombre, email). `detalleCompra` se toma del mensaje.
4. **Plantilla**: `PlantillaEmailService` (Thymeleaf) resuelve `mail/{MOTIVO}.html`. Para `CONFIRMACION_COMPRA` usa nombre/fecha/lugar del evento, nombre de usuario, sector, asientos (`fila-numero, ...` o `General x N` si no numerado) y monto (tolerando `null`).
5. **Persistencia y envío**: se persiste `PENDIENTE` → se marca `ENVIANDO` → `EmailService` envía el `MimeMessage` HTML por SMTP (MailHog en dev) → se marca `ENVIADO` con `fechaEnvio`. Ante fallo se propaga la excepción para activar reintentos.

> Nota: si el correo se envió pero falló la actualización a `ENVIADO`, el reintento encontrará el registro en `ENVIANDO` y no se reenviará (prioridad: evitar duplicados).

## Reintentos y cola de fallidos

- Listener con **3 intentos** y backoff exponencial **1s → 2s → 4s** (`RetryOperationsInterceptor`), `default-requeue-rejected=false`.
- Al agotar reintentos el mensaje se rechaza y el broker lo enruta por **DLX** `enviar.dlx` a la **DLQ** `enviar.dlq`. El registro puede quedar no `ENVIADO`; la reprocesación/gestión de la DLQ es operativa.

## Tecnologías y por qué

| Tecnología | Uso / motivo |
|---|---|
| Spring AMQP + RabbitMQ | Mensajería asíncrona y desacoplada entre servicios; soporte nativo de colas, reintentos y DLQ. |
| Spring Mail (JavaMailSender) + MailHog | Envío SMTP estándar; MailHog como servidor de pruebas en desarrollo (inspección sin correos reales). |
| Thymeleaf | Plantillas HTML por motivo, separando presentación de datos. |

