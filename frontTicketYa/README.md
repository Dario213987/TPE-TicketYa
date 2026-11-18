# TicketYa — Frontend

SPA en React + Vite + TypeScript para el backend de microservicios de TicketYa.

## Requisitos

- Node 20+
- El backend levantado y accesible a través de `api-gateway` (`http://localhost:8080`)

## Desarrollo

```bash
npm install
npm run dev
```

La app queda en `http://localhost:5173`. Vite redirige `/api/*` al gateway
(`http://localhost:8080`), así que en desarrollo no hace falta configurar CORS.

Para apuntar a otra URL, definí `VITE_API_URL` en un `.env` (por ejemplo
`VITE_API_URL=http://mi-host:8080`). Si el front corre en un origen distinto al
gateway, hay que habilitar CORS en el gateway.

## Identidad de prueba

El backend no tiene login: la identidad viaja en los headers `X-User-Id` y
`X-User-Role`. La barra superior permite elegir un usuario de prueba o editar el
id y el rol manualmente. La selección se guarda en `localStorage`.

| Rol | Puede |
|---|---|
| `CLIENTE` | buscar y ver eventos, reservar y pagar, ver sus entradas y QR, preferencias e historial |
| `ORGANIZADOR` | consultar lugares/sectores, crear eventos, definir precios y publicar |
| `ADMINISTRADOR` | crear lugares y sectores, cancelar, suspender y reactivar eventos |

> Los lugares y sectores se crean con rol `ADMINISTRADOR` (así lo exige
> `ms-eventos`). El historial de preferencias queda vacío hasta que `ms-tickets`
> publique los streams `venta.pagada` y `ticket.utilizado` (ver
> `docs/api/ms-preferencias.md`).

## Build

```bash
npm run build    # tsc + vite build
npm run lint     # oxlint
```
