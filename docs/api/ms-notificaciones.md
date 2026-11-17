# ms-notificaciones

Servicio de **notificaciones por email**. Centraliza el envío: consume mensajes desde RabbitMQ, obtiene datos de evento y usuario, renderiza la plantilla según el motivo y envía el correo, persistiendo el resultado (card D8).

## Flujo

1. **Consumo**: `NotificacionListener` recibe `NotificacionEntranteDTO` (`mensajeId`, `motivo`, `eventoId`, `destinatarioId`) desde la cola `enviar`.
2. **Idempotencia**: se valida `mensajeId` (obligatorio). Se busca/crea la notificación por `mensajeId` (único en BD): si ya está `ENVIADO` o `ENVIANDO`, no se reenvía; si está `PENDIENTE` (o es nueva) se continúa. Se maneja condición de carrera por restricción única.
3. **Datos externos**: obtiene evento (`EventoClient` → `ms-eventos`, campos usados: nombre, fecha, lugar) y usuario (`UsuarioClient` → `ms-usuarios`, placeholder) vía Feign.
4. **Plantilla**: `PlantillaEmailService` (Thymeleaf) resuelve la plantilla HTML por `MotivoNotificacion` (`CANCELACION`, `CONFIRMACION_COMPRA`, `PUBLICACION_ARTISTA`, `EDICION`, `SUSPENSION`) con contexto `eventoNombre`, `eventoFecha`, `eventoLugar`, `usuarioNombre`.
5. **Persistencia y envío**: se persiste `PENDIENTE` → se marca `ENVIANDO` → `EmailService` envía el `MimeMessage` HTML por SMTP → se marca `ENVIADO` con `fechaEnvio`. Ante fallo se propaga la excepción para activar reintentos.

> Nota: si el correo se envió pero falló la actualización a `ENVIADO`, el reintento encontrará el registro en `ENVIANDO` y no se reenviará (prioridad: evitar duplicados).

## Reintentos y cola de fallidos

- Listener con **3 intentos** y backoff exponencial **1s → 2s → 4s** (`RetryOperationsInterceptor`), `default-requeue-rejected=false`.
- Al agotar reintentos el mensaje se rechaza y el broker lo enruta por **DLX** `enviar.dlx` a la **DLQ** `enviar.dlq`. El registro puede quedar no `ENVIADO`; la reprocesación/gestión de la DLQ es operativa.

## Tecnologías y por qué

| Tecnología | Uso / motivo |
|---|---|
| Spring AMQP + RabbitMQ | Mensajería asíncrona y desacoplada entre servicios; soporte nativo de colas, reintentos y DLQ. |
| Spring Mail (JavaMailSender) + MailHog | Envío SMTP estándar; MailHog como servidor de pruebas en desarrollo (inspección sin correos reales). |
| Thymeleaf | Plantillas HTML por motivo, separando presentación de datos (stubs actuales; contenido definitivo en otra card). |

