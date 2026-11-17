# Publicación no bloqueante hacia ms-notificaciones

`ms-tickets` y `ms-eventos` publican avisos para `ms-notificaciones` sin hacer esperar al hilo HTTP por RabbitMQ. La operación de negocio (compra, cancelación, cambio de fecha, publicación de artista) confirma primero y luego encola el aviso en un executor acotado.

## Diseño

1. La transacción publica un evento de aplicación: `CompraConfirmadaNotificacionEvent` en `ms-tickets` o `AvisoEventoEvent` en `ms-eventos`.
2. Un `@TransactionalEventListener(phase = AFTER_COMMIT)` recibe el evento. Si la transacción se revierte, el listener no corre.
3. El listener agenda el trabajo en un `ThreadPoolTaskExecutor` propio: 2 hilos y cola de 200 tareas por defecto.
4. El worker arma/publica el `NotificacionEntranteDTO` en la cola `enviar` con `RabbitTemplate`.
5. Si Rabbit falla, el worker loguea el error con `mensajeId` y no relanza. La compra/cancelación ya confirmada no se deshace.
6. Si el executor está lleno, `executor.execute(...)` lanza `RejectedExecutionException`; el listener lo captura y loguea el `mensajeId`. No se usa `CallerRunsPolicy`, porque devolvería el trabajo al hilo HTTP.

El `MdcTaskDecorator` de `ticketya-web-starter` copia el mapa MDC al hilo worker, por lo que `correlationId` y `userId` siguen presentes en los logs de publicación.

No hay outbox ni reintento de la publicación: la garantía es que la operación de negocio no se bloquea, no que el mail se entregue si Rabbit está caído. La idempotencia por `mensajeId` sigue evitando duplicados cuando el mensaje llega a `ms-notificaciones`.

## Configuración

Aplica a `ms-tickets` y `ms-eventos`.

| Propiedad | Variable de entorno | Default | Efecto |
|---|---|---|---|
| `spring.rabbitmq.connection-timeout` | `RABBITMQ_CONNECTION_TIMEOUT_MS` | `2s` | Establecer/leer el socket TCP. |
| `spring.rabbitmq.channel-rpc-timeout` | `RABBITMQ_CHANNEL_RPC_TIMEOUT_MS` | `2s` | Espera de respuestas RPC del canal (declaración de colas, publish con confirm). |
| `spring.rabbitmq.requested-heartbeat` | `RABBITMQ_REQUESTED_HEARTBEAT_S` | `30s` | Heartbeat solicitado al broker. |
| `ticketya.rabbitmq.handshake-timeout-ms` | `RABBITMQ_HANDSHAKE_TIMEOUT_MS` | `2000` | Handshake AMQP, vía `ConnectionFactoryCustomizer`. |
| `ticketya.rabbitmq.shutdown-timeout-ms` | `RABBITMQ_SHUTDOWN_TIMEOUT_MS` | `2000` | Cierre ordenado de la conexión, vía `ConnectionFactoryCustomizer`. |
| `ticketya.notificaciones.async.core-pool-size` | `NOTIFICACIONES_ASYNC_CORE_POOL_SIZE` | `2` | Hilos del executor dedicado. |
| `ticketya.notificaciones.async.queue-capacity` | `NOTIFICACIONES_ASYNC_QUEUE_CAPACITY` | `200` | Tareas pendientes antes de rechazar. |

Antes de este cambio no había timeouts configurados: con Spring Boot 3.5.10 y amqp-client 5.25.0 quedaban `connection-timeout=60s`, `channel-rpc-timeout=10m`, handshake `10s` y heartbeat `60s`.

## Identificadores de mensaje

- `ms-tickets`: `MST-RESERVA-{reservaId}`, generado por `MensajeIdNotificacion.generarDerivado(prefijo, "RESERVA", reservaId)` (sobrecarga agregada al contrato sin romper la versión por motivo/evento que usa `ms-eventos`).
- `ms-eventos`: `MSE-PUBLICACION_ARTISTA-{eventoId}`, `MSE-CANCELACION-{eventoId}` o `MSE-EDICION-{timestamp}-{uuid}`, según el motivo.

Todos los logs de fallo de publicación o de agendado incluyen `mensajeId`.

## Medición

Ejecutar en el entorno con Docker disponible. Exportar primero `RABBITMQ_PASSWORD=123` (o el valor de `infra/.env`).

### 1. Rabbit caído (connection refused)

```bash
docker compose -f infra/docker-compose.yml stop rabbitmq
curl.exe -w '\ntime_total=%{time_total}s status=%{http_code}\n' -X POST http://localhost:<puerto>/<operacion>
docker compose -f infra/docker-compose.yml up -d rabbitmq
```

La respuesta debe llegar en el orden de los 2 segundos, con `2xx` en la operación. En logs debe aparecer `No se pudo publicar ... mensajeId=...` y la transacción debe quedar confirmada.

### 2. Rabbit acepta TCP pero no habla AMQP (blackhole)

```powershell
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 5672)
$listener.Start()
$client = $listener.AcceptTcpClient()
Start-Sleep -Seconds 60
$client.Close()
$listener.Stop()
```

Con `ms-tickets` o `ms-eventos` apuntando a `127.0.0.1:5672`, repetir la request. El handshake debe cortar en ~2s, no en 10s.

### 3. Rabbit funcionando

```bash
docker compose -f infra/docker-compose.yml up -d rabbitmq
curl.exe -w '\ntime_total=%{time_total}s status=%{http_code}\n' -X POST http://localhost:<puerto>/<operacion>
```

El comportamiento funcional no cambia: se publica en `enviar`, `ms-notificaciones` consume y la idempotencia sigue por `(mensajeId, destinatarioId)`.

### 4. Postman

En las colecciones `postman/ms-tickets/ms-tickets.postman_collection.json` y `postman/ms-eventos/ms-eventos.postman_collection.json`, los requests de reserva/cancelación/publicación incluyen un test `pm.test("respuesta no bloqueante", ...)` que muestra `pm.response.responseTime` y valida que sea menor al umbral documentado. Comparar con `RequestLogger` y el header `X-Correlation-Id` para seguir el mismo request en los logs del worker.

Registrar los valores medidos acá:

| Escenario | Servicio | Operación | `time_total` | `pm.response.responseTime` | `mensajeId` logueado |
|---|---|---|---|---|---|
| Rabbit stopped | ms-tickets | compra | pendiente | pendiente | pendiente |
| Rabbit stopped | ms-eventos | publicación | pendiente | pendiente | pendiente |
| Blackhole | ms-tickets | compra | pendiente | pendiente | pendiente |
| Blackhole | ms-eventos | cancelación | pendiente | pendiente | pendiente |
| Rabbit up | ambos | todas | pendiente | pendiente | pendiente |
