# Imágenes Docker por microservicio (US-16.01 / TKY-111)

Cada módulo runtime del monorepo tiene un `Dockerfile` multi-stage que compila el módulo
con Maven + JDK 25 y arma una imagen final con JRE 25, el jar, un usuario no root y un
healthcheck contra Actuator.

> Alcance actual: los 8 módulos runtime que están en `master`: `eureka-server`,
> `api-gateway`, `ms-eventos`, `ms-tickets`, `ms-transacciones`, `ms-devoluciones`,
> `ms-notificaciones` y `ms-preferencias`. `ms-usuarios` y `ms-reportes` todavía no están
> en `master`; cuando se mergeen se les agrega su Dockerfile con el mismo patrón.

## Archivos

| Módulo | Dockerfile | Puerto por defecto | Healthcheck |
|---|---|---|---|
| `eureka-server` | `eureka-server/Dockerfile` | `8761` | `GET /actuator/health` |
| `api-gateway` | `api-gateway/Dockerfile` | `8080` | `GET /actuator/health` |
| `ms-eventos` | `ms-eventos/Dockerfile` | `8080` | `GET /actuator/health` |
| `ms-tickets` | `ms-tickets/Dockerfile` | `8080` | `GET /actuator/health` |
| `ms-transacciones` | `ms-transacciones/Dockerfile` | `8080` | `GET /actuator/health` |
| `ms-devoluciones` | `ms-devoluciones/Dockerfile` | `8080` | `GET /actuator/health` |
| `ms-notificaciones` | `ms-notificaciones/Dockerfile` | `8080` | `GET /actuator/health` |
| `ms-preferencias` | `ms-preferencias/Dockerfile` | `8080` | `GET /actuator/health` |

El archivo `.dockerignore` de la raíz define qué **no** entra al contexto de build
(`target`, `.git`, logs, `docs`, `postman`, `infra` y archivos `.env`).

## Requisitos

- Docker con BuildKit (Docker 24 o superior).
- El build se ejecuta **desde la raíz del monorepo**; el contexto incluye `pom.xml`,
  `libs/`, `contracts/` y todos los módulos.
- Imágenes base:
  - build: `maven:3.9-eclipse-temurin-25`
  - runtime: `eclipse-temurin:25-jre-alpine`
- No hace falta Java ni Maven instalados en el host: la etapa de build los trae.
- No se necesita Docker Compose para construir una imagen individual. El compose
  completo del sistema es US-16.02.

## Cómo funciona cada Dockerfile

1. **Build**: `maven:3.9-eclipse-temurin-25` compila el módulo y sus dependencias del
   reactor con:
   ```bash
   mvn -B -ntp -pl <modulo> -am package -DskipTests
   ```
   Los tests se corren en CI; acá se evita duplicarlos dentro de la imagen. La etapa usa
   un cache mount de BuildKit (`/root/.m2`) para reutilizar dependencias entre builds; el
   caché no forma parte de la imagen final.
2. **Runtime**: `eclipse-temurin:25-jre-alpine` contiene solo el JRE 25 y el jar.
   - se crea el usuario de sistema `app` y se ejecuta con `USER app`;
   - el jar queda en `/app/app.jar`;
   - se crea `/app/logs` con permisos para `app`;
   - `TICKETYA_LOGGING_DIR=/app/logs` hace que el log JSON de `ticketya-logging` se
     escriba en ese directorio;
   - `SERVER_PORT` tiene un valor fijo por imagen (8080, o 8761 para Eureka);
   - `EXPOSE` documenta el puerto;
   - `HEALTHCHECK` consulta `http://127.0.0.1:${SERVER_PORT}/actuator/health` cada 30 s,
     con 60 s de gracia inicial.
3. **Arranque**: `java -jar /app/app.jar`. Para opciones de JVM se puede pasar
   `JAVA_TOOL_OPTIONS` en el `docker run`/compose; no se hornean opciones de memoria en
   la imagen.

## Construir las imágenes

Desde la raíz del monorepo:

```bash
docker build -f eureka-server/Dockerfile -t ticketya/eureka-server:local .
docker build -f api-gateway/Dockerfile -t ticketya/api-gateway:local .
docker build -f ms-eventos/Dockerfile -t ticketya/ms-eventos:local .
docker build -f ms-tickets/Dockerfile -t ticketya/ms-tickets:local .
docker build -f ms-transacciones/Dockerfile -t ticketya/ms-transacciones:local .
docker build -f ms-devoluciones/Dockerfile -t ticketya/ms-devoluciones:local .
docker build -f ms-notificaciones/Dockerfile -t ticketya/ms-notificaciones:local .
docker build -f ms-preferencias/Dockerfile -t ticketya/ms-preferencias:local .
```

El `-f` es obligatorio: el contexto es `.` (raíz), no el directorio del módulo. Si se
ejecuta `docker build ms-eventos`, Maven no encuentra el `pom.xml` padre ni `libs/` y
`contracts/`.

La etiqueta `:local` es la convención de este documento; el registry y el etiquetado
para despliegue quedan fuera de esta card (EP-16 excluye CI/CD y registry).

## Ejecutar una imagen

Las imágenes no incluyen bases, Redis, MongoDB, RabbitMQ, Mailpit ni Eureka. Para una
prueba manual se puede crear una red y usar los contenedores de `infra/docker-compose.yml`
(o cualquier instancia accesible).

Ejemplo con `ms-eventos`, asumiendo Eureka y `db-eventos` ya levantados en la misma red
y publicados con los nombres `eureka-server` y `db-eventos`:

```bash
docker network create ticketya-net

docker run --rm --name ms-eventos --network ticketya-net \
  -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e EUREKA_URL=http://eureka-server:8761/eureka \
  -e DB_URL=jdbc:postgresql://db-eventos:5432/eventos \
  -e DB_USER=eventos \
  -e DB_PASSWORD=cambiar \
  ticketya/ms-eventos:local
```

Para Eureka:

```bash
docker run --rm --name eureka-server --network ticketya-net \
  -p 8761:8761 -e SERVER_PORT=8761 ticketya/eureka-server:local
```

Para el gateway:

```bash
docker run --rm --name api-gateway --network ticketya-net \
  -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e EUREKA_URL=http://eureka-server:8761/eureka \
  ticketya/api-gateway:local
```

Los `ms-*` toman `SERVER_PORT=8080` dentro de su contenedor; si se publican varios en el
host, cada uno necesita un puerto de host distinto (`-p 8081:8080`, `-p 8082:8080`, …).
Eureka los descubre por IP y puerto internos.

### Variables de entorno

Las propiedades ya vienen parametrizadas en cada `application.properties`. Las variables
comunes son:

| Variable | Default en la app | Uso |
|---|---|---|
| `SERVER_PORT` | `8080` en las imágenes (`8761` en Eureka; la app usa `0` si se ejecuta fuera de Docker) | puerto HTTP y healthcheck |
| `EUREKA_URL` | `http://localhost:8761/eureka` | en Docker: `http://eureka-server:8761/eureka` |
| `EUREKA_LEASE_RENEWAL_S` | `5` | renovación de Eureka |
| `EUREKA_LEASE_EXPIRATION_S` | `15` | expiración de Eureka |
| `EUREKA_FETCH_INTERVAL_S` | `5` | refresco del registry |
| `JAVA_TOOL_OPTIONS` | — | opciones JVM, por ejemplo `-Xms256m -Xmx512m` |
| `TICKETYA_LOGGING_DIR` | `/app/logs` en las imágenes | directorio del log JSON |
| `TZ` | zona del contenedor (UTC por defecto) | zona horaria del JVM; relevante para fechas locales |

Por módulo:

#### `eureka-server`

`EUREKA_SELF_PRESERVATION`, `EUREKA_EVICTION_INTERVAL_MS`, `SERVER_PORT`.

#### `api-gateway`

`GATEWAY_CONNECT_TIMEOUT_MS`, `GATEWAY_RESPONSE_TIMEOUT`, `LB_CACHE_TTL`, `EUREKA_URL`.

#### `ms-eventos`

`DB_URL`, `DB_USER`, `DB_PASSWORD` (obligatoria), `LIQUIBASE_CONTEXT`,
`EVENTOS_FINALIZACION_*` (enabled, cron, horas de gracia y locks) y
`EVENTOS_NOTIFICACIONES_*` (enabled, delay, batch, intentos y locks).

#### `ms-tickets`

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`,
`HIBERNATE_JDBC_BATCH_SIZE`, `TICKETS_DISPONIBILIDAD_BATCH_SIZE`,
`TICKETS_RESERVA_*` (límite, expiración, cron y bloqueo), `TICKETS_HABILITACION_*`
(enabled, tamaño de tanda, intervalo, TTL y cron) y `TRANSACCIONES_SERVICE_NAME`.

#### `ms-transacciones`

`DB_URL`, `DB_USER`, `DB_PASSWORD`.

#### `ms-devoluciones`

`DB_URL`, `DB_USER`, `DB_PASSWORD`.

#### `ms-notificaciones`

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`,
`RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `MAIL_HOST`, `MAIL_PORT` y `MAIL_FROM`.

#### `ms-preferencias`

`MONGODB_URI`, `REDIS_HOST`, `REDIS_PORT`, `PREFERENCIAS_HISTORIAL_*` (consumer-enabled,
streams, grupo, consumidor, prefijo/TTL de deduplicación y poll timeout). Si en el futuro
se suma el cliente de eventos de TKY-101, también `EVENTOS_SERVICE_NAME`.

`DB_PASSWORD` y las credenciales de Rabbit/Mail no tienen default seguro: se pasan por
`docker run`/compose y nunca se versionan. El archivo `infra/.env.example` sigue siendo la
base de contraseñas de infraestructura; el compose completo y la matriz de variables de
aplicación se completan en US-16.02.

## Verificar la imagen

Con el contenedor en ejecución:

```bash
curl.exe http://localhost:8080/actuator/health
docker inspect --format '{{json .State.Health}}' ms-eventos
```

Para comprobar el usuario no root:

```bash
docker run --rm --entrypoint id ticketya/ms-eventos:local
# uid=... (app) gid=... (app)
```

Para inspeccionar el log JSON dentro del contenedor:

```bash
docker exec ms-eventos ls -l /app/logs
docker exec ms-eventos tail -n 5 /app/logs/ms-eventos/ms-eventos.json.log
```

El healthcheck queda `healthy` cuando Spring Boot terminó de arrancar y
`/actuator/health` responde 200. Si la base/Redis/Mongo/Rabbit no están disponibles, el
health global puede quedar `DOWN` y el contenedor `unhealthy`; es el comportamiento
esperado hasta que el compose levante las dependencias.

### Resultado de la verificación (2026-11-17)

- Las 8 imágenes construyeron con `docker build` (`exit 0`), desde la raíz del monorepo.
- Los 8 contenedores ejecutan como usuario no root: `uid=100(app) gid=101(app)`.
- `ms-eventos` se ejecutó contra PostgreSQL 18 con Eureka deshabilitado:
  - `/actuator/health` respondió `200` y el healthcheck quedó `healthy`;
  - el log JSON se escribió en `/app/logs/ms-eventos/ms-eventos.json.log`, con dueño
    `app`;
  - el log de healthcheck mostró `ExitCode: 0`.
- `eureka-server` quedó `healthy` y `api-gateway` quedó `healthy`, se registró en Eureka
  (`registration status: 204`) y respondió `200` en `/actuator/health`.
- `ms-tickets`, `ms-transacciones`, `ms-devoluciones`, `ms-notificaciones` y
  `ms-preferencias` se construyeron y se verificó el usuario no root; su prueba de
  runtime completa depende de las bases/Redis/Mongo/Rabbit y queda para el compose de
  US-16.02.

## Cómo se cumple US-16.01

- [x] Multi-stage con **Java 25** (build Maven + JDK 25).
- [x] Imagen final con **JRE 25 y el jar**, sin Maven ni fuentes.
- [x] **Usuario no root** (`app`).
- [x] **Healthcheck** contra Actuator.
- [x] Configuración **parametrizada por variables de entorno**.
- [x] `.dockerignore` para no copiar secretos, `target`, `.git` ni logs.
- [x] Un Dockerfile por módulo runtime.

## Pendientes y límites

- **ms-usuarios** (EP-01) y **ms-reportes** todavía no están en `master`; sus Dockerfiles
  se agregan cuando mergeen. El Jira de TKY-111 dice “Depende de EP-01”, mientras que la
  spec EP-16 dice “Depende de: —”; hay que confirmar si “todos” incluye esos servicios
  para cerrar la card.
- El **docker-compose completo** está en `infra/docker-compose.yml` (TKY-112): réplicas de
  `ms-tickets`, espera por health de las bases, volumen de logs compartido con Alloy y
  Loki/Grafana/MailHog.
- No se agrega build de imágenes a CI/CD: EP-16 lo deja fuera de alcance.
- Las imágenes se construyen desde la raíz del monorepo; el `-f` es obligatorio para
  cada Dockerfile.
- El healthcheck usa `wget` de BusyBox incluido en la base Alpine; no se instalan
  paquetes extra en la imagen final.
