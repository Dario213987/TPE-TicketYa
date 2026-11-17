# Runbook: resiliencia de notificaciones

Este runbook cubre las cuatro pruebas de la matriz de fallos descrita en [Justificación del diseño asíncrono](justificacion-diseno-asincrono.md). Los resultados obtenidos se registran en [Evidencia de resiliencia](../evidencia/resiliencia-notificaciones.md).

## Estado actual

Las pruebas unitarias ya ejecutadas cubren clasificación de errores, reintentos, DLQ e idempotencia. Las pruebas E2E requieren Docker; en el entorno donde se generó este runbook el daemon de Docker no estaba disponible, por lo que las capturas E2E quedan como pasos a completar cuando el entorno esté listo.

## Preparación

Requisitos:

- `infra/docker-compose.yml` levantado con `rabbitmq` y `mailhog`.
- Servicios `ms-tickets`, `ms-notificaciones`, `ms-eventos` y `ms-preferencias` disponibles.
- Postman o `curl.exe` para las operaciones de pago.
- Logs de `ms-tickets` y `ms-notificaciones` a mano.

Variables y credenciales de desarrollo:

```powershell
$env:RABBITMQ_USER = "ticketya"
$env:RABBITMQ_PASSWORD = "123"
$rabbit = "http://localhost:15672"
$mailhog = "http://localhost:8025"
$cred = [pscredential]::new(
  $env:RABBITMQ_USER,
  (ConvertTo-SecureString $env:RABBITMQ_PASSWORD -AsPlainText -Force))
```

Levantar dependencias:

```bash
docker compose -f infra/docker-compose.yml up -d rabbitmq mailhog
```

Publicar un mensaje de prueba en `enviar` (el header `__TypeId__` es obligatorio):

```powershell
$mensajeId = "MST-RESERVA-99"
$payload = @{
  mensajeId = $mensajeId
  motivo = "CONFIRMACION_COMPRA"
  eventoId = 10
  destinatarioId = 20
} | ConvertTo-Json -Compress

$publicacion = @{
  properties = @{
    delivery_mode = 2
    content_type = "application/json"
    headers = @{ "__TypeId__" = "org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO" }
  }
  routing_key = "enviar"
  payload = $payload
  payload_encoding = "string"
} | ConvertTo-Json -Compress -Depth 6

Invoke-RestMethod -Method Post `
  -Uri "$rabbit/api/exchanges/%2F/amq.default/publish" `
  -Credential $cred `
  -ContentType "application/json" `
  -Body $publicacion
```

Limpiar MailHog antes de cada prueba:

```powershell
Invoke-RestMethod -Method Delete "$mailhog/api/v1/messages"
```

Para acortar la prueba de reintentos, reiniciar `ms-notificaciones` con:

```powershell
$env:REINTENTOS_MAX_INTENTOS = "3"
$env:REINTENTOS_INTERVALO_INICIAL_MS = "1000"
$env:REINTENTOS_MULTIPLICADOR = "2.0"
$env:REINTENTOS_INTERVALO_MAXIMO_MS = "2000"
```

## E2E-01: SMTP caído y recuperación desde DLQ

Objetivo: demostrar que un fallo de correo no pierde el estado de la notificación y que la republicación con el mismo `mensajeId` no duplica el envío.

Pasos:

1. Detener MailHog:

   ```bash
   docker compose -f infra/docker-compose.yml stop mailhog
   ```

2. Publicar el mensaje de preparación en `enviar`.

3. Observar los logs de `ms-notificaciones`. Deben aparecer intentos con backoff y, al agotarlos, `Reintentos agotados: mensajeId=MST-RESERVA-99`.

4. Verificar la DLQ:

   ```powershell
   Invoke-RestMethod -Method Get `
     -Uri "$rabbit/api/queues/%2F/enviar.dlq" `
     -Credential $cred
   ```

   `messages` debe ser mayor que cero.

5. Verificar en la base de datos que la fila de `MST-RESERVA-99` quedó en `FALLIDO` con `ultimo_error`.

6. Levantar MailHog:

   ```bash
   docker compose -f infra/docker-compose.yml start mailhog
   ```

7. Tomar el payload de la DLQ y republicarlo exactamente igual en `enviar`.

8. Verificar en `http://localhost:8025` que llegó un correo. Republicar el mismo payload una segunda vez y verificar que no aparece un segundo correo: en logs debe aparecer `Mensaje duplicado omitido`.

Criterios de aceptación:

- La compra/pago original no falló por el correo.
- Existe evidencia de 3 intentos (u 8 con defaults) antes de DLQ.
- La fila quedó `FALLIDO` con `ultimo_error`.
- Después de la republicación hay exactamente 1 correo.

Capturas: `docs/evidencia/capturas/01-smtp-caido-dlq.png`, `docs/evidencia/capturas/01-republicacion-mailhog.png`.

## E2E-02: `ms-notificaciones` caído durante la compra

Objetivo: demostrar que pagar una reserva responde aunque el consumidor esté caído y que el aviso se procesa al volver.

Pasos:

1. Dejar Rabbit y MailHog levantados.
2. Detener `ms-notificaciones`.
3. Ejecutar la request de pago de `postman/ms-tickets/pagar-reserva.postman_collection.json`, o el `curl.exe` equivalente, con `-w 'time_total=%{time_total}s status=%{http_code}'`.
4. Verificar que la respuesta es `2xx` y que `time_total` está por debajo del umbral no bloqueante (5 s).
5. Consultar la cola `enviar`: debe quedar al menos un mensaje pendiente.
6. Levantar `ms-notificaciones`.
7. Verificar que la cola se drena, que el correo aparece en MailHog y que no se generó una segunda venta.

Criterios de aceptación:

- `status=2xx`, `status` de pago correcto.
- Mensaje en `enviar` mientras el consumidor está caído.
- Correo entregado tras reiniciar el consumidor.
- Una sola venta para la misma reserva.

Capturas: `docs/evidencia/capturas/02-ms-notificaciones-caido.png`, `docs/evidencia/capturas/02-cola-y-mailhog.png`.

## E2E-03: Rabbit caído durante la compra

Objetivo: demostrar que la pérdida del broker no revierte ni bloquea la compra.

Pasos:

1. Detener Rabbit:

   ```bash
   docker compose -f infra/docker-compose.yml stop rabbitmq
   ```

2. Ejecutar la misma request de pago con medición de tiempo.
3. Revisar logs de `ms-tickets`: debe aparecer `No se pudo publicar notificación de confirmación de compra (compra ya confirmada): mensajeId=MST-RESERVA-{id}`.
4. Verificar en la respuesta y en la base de datos que la reserva quedó pagada.
5. Levantar Rabbit:

   ```bash
   docker compose -f infra/docker-compose.yml start rabbitmq
   ```

6. Opcionalmente, reconstruir y republicar el mensaje con el mismo `mensajeId` para entregar el correo fuera de la transacción original.

Criterios de aceptación:

- Respuesta `2xx` en menos de 5 s con Rabbit caído.
- Log de error de publicación con `mensajeId` y `reservaId`.
- Compra confirmada en base de datos.
- Se registra explícitamente que el mail no se entregó automáticamente.

Capturas: `docs/evidencia/capturas/03-rabbit-caido-logs.png`, `docs/evidencia/capturas/03-compra-confirmada.png`.

## E2E-04: executor rechazado / saturado

Objetivo: demostrar que un rechazo del executor no se propaga al hilo HTTP.

Opción unitaria reproducible:

```powershell
& "C:\Users\tati2\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd" `
  -pl ms-tickets -am `
  "-Dtest=NotificacionCompraEventListenerTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

El test `rechazoDelExecutorNoSePropaga` usa un `Executor` que lanza `RejectedExecutionException`, genera el log `No se pudo agendar notificación de compra (compra ya confirmada): mensajeId=MST-RESERVA-7` y verifica que el publicador no fue invocado.

Opción E2E (opcional):

1. Rearrancar `ms-tickets` con:

   ```powershell
   $env:NOTIFICACIONES_ASYNC_CORE_POOL_SIZE = "1"
   $env:NOTIFICACIONES_ASYNC_QUEUE_CAPACITY = "1"
   ```

2. Disparar varias compras en paralelo.
3. Verificar que todas las respuestas HTTP son `2xx`.
4. Buscar en logs `No se pudo agendar ... mensajeId=...`.

Criterios de aceptación:

- Ninguna request HTTP falla por el rechazo.
- Log con `mensajeId` y `reservaId`.
- Test unitario en verde.

Capturas: `docs/evidencia/capturas/04-executor-rechazado.png`.

## Matriz de cobertura

| Prueba | Fallo simulado | Resultado esperado | Evidencia |
|---|---|---|---|
| E2E-01 | SMTP caído → DLQ → republicación | Reintentos, `FALLIDO`, 1 correo | [Evidencia](../evidencia/resiliencia-notificaciones.md) |
| E2E-02 | `ms-notificaciones` caído | Compra 2xx, mensaje pendiente, entrega al volver | [Evidencia](../evidencia/resiliencia-notificaciones.md) |
| E2E-03 | Rabbit caído | Compra 2xx, log con `mensajeId`, aviso no entregado | [Evidencia](../evidencia/resiliencia-notificaciones.md) |
| E2E-04 | Executor rechazado | Sin excepción hacia HTTP, log de agendado | [Evidencia](../evidencia/resiliencia-notificaciones.md) |

## Rollback operativo

- Si los reintentos son demasiado lentos, bajar temporalmente `REINTENTOS_MAX_INTENTOS`.
- Si la DLQ crece, no borrar mensajes sin antes guardar el payload y el `mensajeId`.
- Si se aumenta el executor, revisar `NOTIFICACIONES_ASYNC_CORE_POOL_SIZE` y `NOTIFICACIONES_ASYNC_QUEUE_CAPACITY` en ambos productores (`ms-tickets` y `ms-eventos`).
- Nunca republicar cambiando el `mensajeId`: se perdería la idempotencia.
