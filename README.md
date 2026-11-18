# TicketYa

Plataforma de venta de entradas basada en microservicios.
TPE de Arquitectura Web (TUDAI) 2026.

## Requisitos

- **JDK 25**
- Maven 3.9+
- Docker

Verificá con `mvn -v` que Maven use Java 25.

## Estructura

| Carpeta                                                                    | Contenido |
|----------------------------------------------------------------------------|---|
| `libs/`                                                                    | Librerías compartidas de infraestructura (`ticketya-core`, `ticketya-web-starter`) |
| `contracts/`                                                               | Contratos OpenAPI, uno por microservicio |
| `eureka-server/`, `api-gateway/`                                           | Descubrimiento y punto de entrada único |
| `ms-eventos/`, `ms-tickets/`, `ms-transacciones/`, `ms-devoluciones/`, `ms-notificaciones/`, etc | Microservicios de negocio |
| `specs/`                                                                   | Propuestas y specs |
| `postman/`                                                                 | Colección de requests |

## Reglas

- Ningún `ms-*` depende de otro `ms-*`; solo de `libs` y `contracts`.
- Las `libs` no contienen lógica de negocio.

## Build

    mvn verify                          # todo el proyecto
    mvn -pl ms-eventos -am package      # un servicio y lo que necesita

## Ejecución

### 1. Infraestructura

    cp infra/.env.example infra/.env   # completar contraseñas
    docker compose -f infra/docker-compose.yml up -d

Levanta una instancia de base de datos por microservicio (PostgreSQL por defecto, MongoDB en `ms-preferencias`):

| Servicio | Puerto en el host |
|---|---|
| ms-eventos | 5433 |
| ms-tickets | 5434 |
| ms-transacciones | 5435 |
| ms-devoluciones | 5436 |
| ms-notificaciones | 5437 |
| ms-preferencias (MongoDB) | 5438 |

Cada servicio puede usar PostgreSQL o MySQL. Para pasar uno a MySQL, seguí los pasos de la plantilla comentada en `infra/docker-compose.yml` (bloque del compose, driver en el pom y `DB_URL`).

`ms-preferencias` guarda documentos en MongoDB (`db-preferencias`, puerto 5437) y se configura con la variable `MONGODB_URI`.

Redis se usa en `ms-tickets` para la fila virtual de eventos de alta demanda (`redis-tickets`, puerto 6380 en el host). Un job periódico habilita a los clientes por tandas y emite pases con TTL; la coordinación entre instancias se apoya en `habilitacion_eventos` y en scripts Lua atómicos. Ver [`docs/api/ms-tickets-fila.md`](docs/api/ms-tickets-fila.md).

Redis Streams se usan en `ms-preferencias` para el historial de compras y asistencias (`redis-preferencias`, puerto 6381 en el host). Ver [`docs/api/ms-preferencias.md`](docs/api/ms-preferencias.md).

RabbitMQ se usa como broker de mensajería (`rabbitmq`, AMQP `5672`, UI `15672`, credenciales `RABBITMQ_USER`/`RABBITMQ_PASSWORD`/`RABBITMQ_VHOST` en `infra/.env`). MailHog provee un SMTP de pruebas para `ms-notificaciones` (SMTP `1025`, UI `8025`). Para levantar `ms-notificaciones` se requieren `DB_PASSWORD`, `RABBITMQ_*` y el broker/MailHog en ejecución. El listener reintenta con backoff exponencial y DLQ; el envío SMTP corta a los 2 s de conexión y 5 s de lectura/escritura (`MAIL_SMTP_CONNECTION_TIMEOUT_MS`/`MAIL_CONNECTION_TIMEOUT_MS`, `MAIL_SMTP_TIMEOUT_MS`/`MAIL_READ_TIMEOUT_MS`, `MAIL_SMTP_WRITE_TIMEOUT_MS`/`MAIL_WRITE_TIMEOUT_MS`).

Las variables de usuario y contraseña solo se aplican con el volumen vacío. Para cambiarlas, o para cambiar el motor de un servicio que ya tenía datos, hay que recrear con `docker compose -f infra/docker-compose.yml down -v` (borra los datos).

### 2. Instalar las libs (una vez, y cada vez que cambien)

    mvn install -DskipTests

Instala el pom padre y las libs en el repositorio local de Maven, para poder correr un módulo suelto.

### 3. Levantar un módulo

Primero Eureka y después lo demás:

    mvn -pl eureka-server spring-boot:run
    mvn -pl api-gateway spring-boot:run
    env DB_PASSWORD=<contraseña de su base> mvn -pl ms-eventos spring-boot:run

Los `ms-*` necesitan `DB_PASSWORD` (la de `infra/.env` para ese servicio). Eureka queda en http://localhost:8761.

`ms-tickets` firma los códigos QR con HMAC usando `TICKETS_QR_SECRET`. En desarrollo hay un valor por defecto (`dev-secret-cambiar`), pero en producción hay que definir un secreto propio y largo (también está en `infra/.env.example`):

    env DB_PASSWORD=<...> TICKETS_QR_SECRET=<secreto-largo> mvn -pl ms-tickets spring-boot:run

La firma se comprueba en `AccesoService.validar` antes de consultar la base de datos: un QR con firma inválida responde 400 sin acceder al repositorio. Esto aplica a los QRs emitidos con firma; los tickets anteriores tienen un `codigo_qr` sin firma y serán rechazados con 400, así que hay que re-firmarlos (`firmar(codigo_qr)`) antes de desplegar si existen datos previos.

### 4. Levantar dos instancias del mismo servicio

Los `ms-*` usan puerto aleatorio (`server.port=0`) y un id de instancia único, así que alcanza con ejecutar el mismo comando en dos terminales:

    env DB_PASSWORD=<...> mvn -pl ms-eventos spring-boot:run   # terminal 1
    env DB_PASSWORD=<...> mvn -pl ms-eventos spring-boot:run   # terminal 2

En el panel de Eureka aparecen las dos instancias de `MS-EVENTOS`, cada una con su puerto (puede tardar hasta 30 segundos). Ambas comparten la base del servicio.

También sirve con el jar:

    java -jar ms-eventos/target/ms-eventos-1.0-SNAPSHOT.jar

### Desde IntelliJ

Para dos instancias, en la configuración de ejecución del servicio activá **Modify options → Allow multiple instances** y ejecutala dos veces. Cargá `DB_PASSWORD` en *Environment variables*.

## Logging

Todos los servicios dependen de `ticketya-logging`, que define el formato: consola en texto plano y archivo JSON en `logs/<servicio>/<servicio>.json.log`, con rotación por tamaño y fecha. No se define un `logback` propio en los servicios.

Para loguear se usa `@Slf4j` (Lombok) o `LoggerFactory.getLogger(...)`.

Propiedades opcionales: `ticketya.logging.dir` (`logs`), `ticketya.logging.max-file-size` (`10MB`), `ticketya.logging.max-history` (`7` días) y `ticketya.logging.total-size-cap` (`500MB`).

### Logs centralizados (Loki + Alloy + Grafana)

El compose de `infra/` también levanta Loki, Alloy y Grafana. Alloy lee los `*.json.log` de `logs/` y de `<módulo>/logs/` (según desde dónde se lanzó el servicio), etiqueta cada línea con `service` y la manda a Loki.

En Grafana (http://localhost:3000, sin login) el datasource Loki ya está cargado. En **Explore** se consulta por servicio:

    {service="ms-eventos"}

Si se cambia `ticketya.logging.dir` a una carpeta fuera del repo, Alloy no la ve.

El dashboard **TicketYa - Logs** (carpeta TicketYa, http://localhost:3000/d/ticketya-logs) se carga solo desde `infra/grafana/provisioning/dashboards/`. Tiene filtros por servicio y por correlation ID (aplicados a todos los paneles), tasa de errores 5xx, latencia p95 por endpoint y los logs. Para cambiarlo, editar el JSON y reiniciar Grafana: desde la UI no se guarda.

## Log de operaciones críticas

`OperationLogger` (`ticketya-core`) escribe una línea por operación crítica en el logger `ticketya.operations`, con la operación, el usuario, la entidad, su id, el resultado (`OK`, `FAILED` o `COMPENSATED`) y el motivo si no salió bien. Los mismos datos quedan como campos del JSON (`operation`, `userId`, `entity`, `entityId`, `result`, `reason`), así que en Grafana se puede filtrar por ellos: `{service="ms-tickets"} | json | operation="PURCHASE"`.

| Operación | Dónde | Usuario / entidad | Resultados |
|---|---|---|---|
| `PURCHASE` | `PagoService` (ms-tickets): pago de una reserva y reconciliación de pagos pendientes | cliente / `Reserva` | `OK` entradas emitidas; `FAILED` pago rechazado o error (si no se pudo reintegrar lo cobrado, se aclara en el motivo); `COMPENSATED` se cobró, no se pudieron emitir las entradas y se reintegró. Un pago que queda `PENDIENTE_PAGO` no se registra hasta que se resuelve, y un reintento idempotente de una compra terminada tampoco. |
| `REFUND` | `ReintegroMasivoService` (ms-devoluciones): reintegro por cancelación del evento, una línea por entrada | cliente / `Ticket` | `OK` acreditada; `FAILED` no se pudo acreditar (se reintenta) |
| `REFUND` | `DevolucionService.rechazarDevolucion` (ms-devoluciones) | cliente / `Ticket` | `FAILED` con `rechazada: <motivo>` |
| `EVENT_CANCEL` | `EventoController` (ms-eventos) | administrador / `Evento` | `OK` / `FAILED` |
| `ACCESS` | `AccesoController` (ms-tickets) | personal de acceso / `Ticket` | `OK` / `FAILED` |

## Correlation id

Cada operación lleva un id (`X-Correlation-Id`) que viaja por todos los servicios: el gateway lo genera si el cliente no lo manda (solo acepta `A-Z a-z 0-9 . _ -`, hasta 64 caracteres), lo devuelve en la respuesta y los servicios lo propagan en las llamadas Feign. Aparece en todas las líneas de log y en el campo `correlationId` de los errores.
