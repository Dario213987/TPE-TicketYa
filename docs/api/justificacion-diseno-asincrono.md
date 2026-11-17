# Justificación del diseño asíncrono de notificaciones

- Estado: Aceptado.
- Alcance: flujo de compra/pago (`TKY-103`), publicación no bloqueante (`TKY-104`), recomendaciones (`TKY-105`) y tolerancia a fallos (`TKY-106`).

## Contexto

La confirmación de una compra no puede depender de que RabbitMQ, `ms-notificaciones`, SMTP o MailHog estén disponibles. Si el hilo HTTP espera al broker o al envío de correo, un fallo ajeno a la venta puede transformar una compra correcta en un error para el cliente, o dejar una transacción inconsistente.

Además, `ms-notificaciones` necesita idempotencia: un reintento de Rabbit o una republicación operativa no debe generar dos correos para la misma reserva.

## Decisión

La operación de negocio confirma primero y publica el aviso después:

1. `ReservaPagoService` publica `CompraConfirmadaNotificacionEvent` dentro de la transacción.
2. `NotificacionCompraEventListener` escucha con `@TransactionalEventListener(phase = AFTER_COMMIT)`, por lo que un rollback no genera notificación.
3. El listener agenda el trabajo en `notificacionesAsyncExecutor` (2 hilos y cola de 200 por defecto), con `MdcTaskDecorator` para preservar `correlationId` y `userId`.
4. El worker construye `NotificacionEntranteDTO` con `mensajeId = MST-RESERVA-{reservaId}` y publica en la cola `enviar`.
5. `ms-notificaciones` consume, resuelve destinatarios, persiste el estado y envía el correo. Los fallos transitorios se reintentan 8 veces con backoff exponencial; los definitivos terminan en `enviar.dlq`.
6. La idempotencia se resuelve por `(mensajeId, destinatarioId)`.

El mismo patrón aplica a `ms-eventos` para `PUBLICACION_ARTISTA`, `CANCELACION` y `EDICION`.

## Alternativas descartadas

| Alternativa | Motivo de descarte |
|---|---|
| Enviar el correo de forma síncrona en el request de pago | Acopla la compra a SMTP y a `ms-notificaciones`; un timeout de correo bloquearía al cliente. |
| Publicar en Rabbit dentro de la transacción, antes del commit | Un rollback dejaría un aviso de una compra que no existe. |
| Anotar el listener con `@Async` directo | El rechazo del executor o un error de publicación anterior al `try/catch` puede propagarse al hilo que publicó el evento; el executor queda fuera de control. |
| `CallerRunsPolicy` en el executor | Devuelve el trabajo al hilo HTTP cuando la cola se llena, reintroduciendo bloqueo. |
| Outbox transaccional | Requiere nueva tabla, job de reenvío y operación adicional. Queda fuera del alcance: la garantía buscada es no bloquear la compra, no entregar el mail si Rabbit está caído. |

## Consecuencias

Positivas:

- La compra responde aunque Rabbit o el servicio de notificaciones estén caídos.
- No se notifican transacciones revertidas.
- Los logs de fallo incluyen `mensajeId` y `reservaId`, lo que permite correlacionar y republicar.
- Los reintentos y la DLQ hacen explícitos los fallos de entrega sin perder el estado de la notificación.
- La idempotencia evita duplicados al republicar.

Negativas / riesgos aceptados:

- Si Rabbit está caído en el momento de publicar, el aviso no se entrega automáticamente; se requiere republicación manual.
- Si el executor está lleno, la tarea se descarta con log; se compensa ajustando `NOTIFICACIONES_ASYNC_CORE_POOL_SIZE` y `NOTIFICACIONES_ASYNC_QUEUE_CAPACITY`.
- Un timeout SMTP deja la fila en `ENVIANDO` y requiere reconciliación con MailHog antes de reenviar; se prioriza evitar correos duplicados.

## Matriz de fallos

| ID | Fallo | Detección | Comportamiento | ¿Bloquea la compra? | Recuperación / evidencia |
|---|---|---|---|---|---|
| F1 | Rabbit caído al publicar | `AmqpConnectException` en logs del worker | Log `No se pudo publicar ... mensajeId=...`; no se relanza | No | Republicar el payload original cuando Rabbit vuelva. Ver E2E-03. |
| F2 | Executor lleno o rechazado | `RejectedExecutionException` | Log `No se pudo agendar ... mensajeId=...` | No | Aumentar pool/cola o reenviar manualmente. Ver E2E-04. |
| F3 | SMTP caído o error 4xx | `ConnectException` / SMTP 4xx | Fila `PENDIENTE` con `ultimo_error`, 8 reintentos y DLQ | No | Corregir MailHog y republicar el mismo `mensajeId`. Ver E2E-01. |
| F4 | Timeout SMTP | `SocketTimeoutException` | Fila `ENVIANDO`, sin relanzar | No | Verificar MailHog y reconciliar antes de reenviar. Ver E2E-01. |
| F5 | Dirección inválida / auth inválida | `EmailPermanenteException` | `NO_ENTREGABLE` o `FALLIDO`, sin reintentos | No | Corregir datos o credenciales; no republicar ciegamente. Ver E2E-01. |
| F6 | `ms-notificaciones` caído | Conexión rechazada por el consumidor | Mensaje permanece en `enviar`; la compra ya respondió | No | Levantar el servicio y dejar que consuma la cola. Ver E2E-02. |
| F7 | Mensaje duplicado | Fila existente por `(mensajeId, destinatarioId)` | `Mensaje duplicado omitido` en WARN | No | Sin acción; Ver E2E-01. |
| F8 | Fallo sin `mensajeId` en DLQ | `NotificacionDlqRecoverer` | Log `Reintentos agotados sin mensajeId` y rechazo sin reencolado | No | Reconstruir el payload con `mensajeId` desde el productor. Ver E2E-01. |

## Configuración relevante

| Propiedad | Default |
|---|---|
| `spring.rabbitmq.connection-timeout` / `channel-rpc-timeout` | `2s` |
| `ticketya.rabbitmq.handshake-timeout-ms` / `shutdown-timeout-ms` | `2000` |
| `ticketya.notificaciones.async.core-pool-size` | `2` |
| `ticketya.notificaciones.async.queue-capacity` | `200` |
| `REINTENTOS_MAX_INTENTOS` | `8` |
| `REINTENTOS_INTERVALO_INICIAL_MS` | `5000` |
| `REINTENTOS_MULTIPLICADOR` | `2.0` |
| `REINTENTOS_INTERVALO_MAXIMO_MS` | `300000` |

## Enlaces

- [Publicación no bloqueante](publicacion-no-bloqueante.md)
- [Documentación de `ms-notificaciones`](ms-notificaciones.md)
- [Runbook de resiliencia](runbook-resiliencia-notificaciones.md)
- [Evidencia de pruebas](../evidencia/resiliencia-notificaciones.md)
