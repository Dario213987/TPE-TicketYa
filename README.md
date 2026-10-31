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

TODO