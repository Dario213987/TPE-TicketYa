# Evidencia: resiliencia de notificaciones

Fecha de captura: 2026-11-17.
Rama: `TKY-106`.

## Resumen

| ID | Prueba | Estado | Tipo de evidencia |
|---|---|---|---|
| E2E-01 | SMTP caído, reintentos, DLQ y republicación | Unitario verde; E2E pendiente de Docker | Logs JUnit + capturas pendientes |
| E2E-02 | `ms-notificaciones` caído durante la compra | E2E pendiente de Docker | Capturas pendientes |
| E2E-03 | Rabbit caído durante la compra | Unitario verde; E2E pendiente de Docker | Logs JUnit + capturas pendientes |
| E2E-04 | Executor rechazado | Unitario verde; E2E pendiente de Docker | Logs JUnit + capturas pendientes |

El daemon de Docker no estaba disponible en el entorno de captura:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
Could not find a valid Docker environment. Please check configuration.
```

Por eso no se generaron capturas de Rabbit Management, MailHog ni contenedores caídos. Los pasos para completarlas están en el [runbook](../api/runbook-resiliencia-notificaciones.md).

## Comando ejecutado

```powershell
& "C:\Users\tati2\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd" `
  -pl ms-tickets,ms-notificaciones -am `
  "-Dtest=NotificacionCompraEventListenerTest,NotificacionCompraPublicadorTest,NotificacionCompraMensajeFactoryTest,NotificacionDlqRecovererTest,NotificacionServiceTest,EmailServiceTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Resultado:

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.222 s -- in org.tudai.ticketya.tickets.notificacion.NotificacionCompraEventListenerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in org.tudai.ticketya.tickets.notificacion.NotificacionCompraMensajeFactoryTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.202 s -- in org.tudai.ticketya.tickets.notificacion.NotificacionCompraPublicadorTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0

[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.736 s -- in org.tudai.ticketya.notificaciones.config.NotificacionDlqRecovererTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.801 s -- in org.tudai.ticketya.notificaciones.service.EmailServiceTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.729 s -- in org.tudai.ticketya.notificaciones.service.NotificacionServiceTest
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

## E2E-01: SMTP caído, reintentos, DLQ y republicación

Evidencia unitaria de agotamiento de reintentos y envío a DLQ:

```text
17:28:51.695 [main] ERROR org.tudai.ticketya.notificaciones.config.NotificacionDlqRecoverer -- Reintentos agotados: mensajeId=MST-1-u
```

Evidencia unitaria de error transitorio y clasificación:

```text
17:28:53.554 [main] WARN org.tudai.ticketya.notificaciones.service.NotificacionService -- Mensaje duplicado omitido: mensajeId=MST-1-u, destinatarioId=21, estado=ENVIADO
17:28:53.648 [main] WARN org.tudai.ticketya.notificaciones.service.NotificacionService -- Mensaje duplicado omitido: mensajeId=MST-1-u, destinatarioId=20, estado=ENVIADO
```

Evidencia unitaria de timeout y error permanente:

```text
17:28:54.104 [main] ERROR org.tudai.ticketya.notificaciones.service.NotificacionService -- Timeout de SMTP: mensajeId=MST-1-u, destinatarioId=20
17:28:54.207 [main] WARN org.tudai.ticketya.notificaciones.service.NotificacionService -- Notificacion no entregable (sin email): mensajeId=MST-1-u, destinatarioId=20
17:28:54.276 [main] ERROR org.tudai.ticketya.notificaciones.service.NotificacionService -- Error permanente de SMTP: mensajeId=MST-1-u, destinatarioId=20
```

Cobertura: `NotificacionDlqRecovererTest`, `EmailServiceTest`, `NotificacionServiceTest`.

Capturas E2E pendientes:

- `capturas/01-smtp-caido-dlq.png`: logs de reintentos y mensaje en `notificaciones.fallidas`.
- `capturas/01-republicacion-mailhog.png`: correo recibido en MailHog y log de duplicado omitido.

## E2E-02: `ms-notificaciones` caído durante la compra

Estado: pendiente de ejecución E2E. La cobertura unitaria equivalente demuestra que la publicación ocurre después del commit y en un executor separado (`NotificacionCompraEventListenerTest`, `commitPublicaYRollbackNoPublica`).

Capturas E2E pendientes:

- `capturas/02-ms-notificaciones-caido.png`: request de pago `2xx` con el servicio detenido.
- `capturas/02-cola-y-mailhog.png`: mensaje en `enviar` y correo entregado tras levantar el servicio.

## E2E-03: Rabbit caído durante la compra

Evidencia unitaria del comportamiento no bloqueante cuando falla la publicación:

```text
17:28:35.425 [main] ERROR org.tudai.ticketya.tickets.notificacion.NotificacionCompraPublicador -- No se pudo publicar notificación de confirmación de compra (compra ya confirmada): mensajeId=MST-2-u, reservaId=7, eventoId=1, destinatarioId=2
```

Evidencia unitaria de construcción del `mensajeId` derivado de la reserva:

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in org.tudai.ticketya.tickets.notificacion.NotificacionCompraMensajeFactoryTest
```

Cobertura: `NotificacionCompraPublicadorTest`, `NotificacionCompraMensajeFactoryTest`.

Capturas E2E pendientes:

- `capturas/03-rabbit-caido-logs.png`: error de publicación con `mensajeId` y `reservaId`.
- `capturas/03-compra-confirmada.png`: respuesta de pago y compra persistida.

## E2E-04: Executor rechazado

Evidencia unitaria del rechazo del executor:

```text
17:28:32.122 [main] ERROR org.tudai.ticketya.tickets.notificacion.NotificacionCompraEventListener -- No se pudo agendar notificación de compra (compra ya confirmada): mensajeId=MST-RESERVA-7, reservaId=7
```

El test `rechazoDelExecutorNoSePropaga` verifica que `listener.onCompraConfirmada(...)` no lanza excepción y que el publicador no es invocado.

Captura E2E pendiente:

- `capturas/04-executor-rechazado.png`: RequestLogger con todas las compras `2xx` y log de agendado fallido.

## Suite completa de la rama

También se ejecutó la suite no-Docker completa sobre `TKY-106` con estas exclusiones:

```text
!LiquibaseMigrationTest,!EventoRepositoryEstadosVencidosTest,!NotificacionesApplicationTests,!NoDobleVentaTest
```

Resultado:

```text
[INFO] TicketYa Contract - notificaciones ................. SUCCESS
[INFO] TicketYa ms-eventos ................................ SUCCESS
[INFO] TicketYa ms-tickets ................................ SUCCESS
[INFO] TicketYa ms-notificaciones ......................... SUCCESS
[INFO] TicketYa ms-preferencias ........................... SUCCESS
[INFO] BUILD SUCCESS
```

`ms-notificaciones`: 68 tests, 0 failures, 0 errors.
`ms-preferencias`: 79 tests, 0 failures, 0 errors, 10 skipped por `disabledWithoutDocker`.

## Cómo completar las capturas

1. Seguir el [runbook](../api/runbook-resiliencia-notificaciones.md).
2. Guardar las imágenes en `docs/evidencia/capturas/` con los nombres indicados.
3. Reemplazar cada estado `pendiente` de la tabla resumen por `Ejecutado` y agregar la fecha/hora de la captura.
