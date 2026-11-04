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
| `ms-eventos/`, `ms-tickets/`, `ms-transacciones/`, `ms-devoluciones/`, etc | Microservicios de negocio |
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

Levanta una instancia de base de datos por microservicio (PostgreSQL por defecto):

| Servicio | Puerto en el host |
|---|---|
| ms-eventos | 5433 |
| ms-tickets | 5434 |
| ms-transacciones | 5435 |
| ms-devoluciones | 5436 |

Cada servicio puede usar PostgreSQL o MySQL. Para pasar uno a MySQL, seguí los pasos de la plantilla comentada en `infra/docker-compose.yml` (bloque del compose, driver en el pom y `DB_URL`).

Redis está desactivado por ahora (ver el comentario en el compose).

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

## Correlation id

Cada operación lleva un id (`X-Correlation-Id`) que viaja por todos los servicios: el gateway lo genera si el cliente no lo manda (solo acepta `A-Z a-z 0-9 . _ -`, hasta 64 caracteres), lo devuelve en la respuesta y los servicios lo propagan en las llamadas Feign. Aparece en todas las líneas de log y en el campo `correlationId` de los errores.
