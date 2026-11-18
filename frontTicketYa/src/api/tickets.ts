import { api, buildQuery } from './http'
import type {
  CompraResponse,
  DisponibilidadEvento,
  DisponibilidadResponse,
  MedioPago,
  MiEntrada,
  Paged,
  PosicionFila,
  PublicarDisponibilidadRequest,
  QrEntrada,
  ReporteEntradasVendidas,
  ReporteOcupacion,
  ReporteRecaudacion,
  ReservaRequest,
  ReservaResponse,
  ResultadoAcceso,
  TurnoFila,
  ValidarAccesoRequest,
} from './types'

export function publicarDisponibilidad(eventoId: number, body: PublicarDisponibilidadRequest) {
  return api<DisponibilidadResponse>(`/api/tickets/eventos/${eventoId}/disponibilidad`, {
    method: 'POST',
    body,
  })
}

export function consultarDisponibilidad(eventoId: number) {
  return api<DisponibilidadEvento>(`/api/tickets/eventos/${eventoId}/disponibilidad`)
}

export function reservar(eventoId: number, body: ReservaRequest) {
  return api<ReservaResponse>(`/api/tickets/eventos/${eventoId}/reservas`, {
    method: 'POST',
    body,
  })
}

export function pagarReserva(reservaId: number, medioPago: MedioPago, idempotencyKey: string) {
  return api<CompraResponse>(`/api/tickets/reservas/${reservaId}/pago`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { medioPago },
  })
}

export function listarMisEntradas(page = 0, size = 20) {
  return api<Paged<MiEntrada>>(`/api/tickets/mis-entradas${buildQuery({ page, size })}`)
}

export function obtenerQr(ticketId: number) {
  return api<QrEntrada>(`/api/tickets/${ticketId}/qr`)
}

export function entrarEnFila(eventoId: number) {
  return api<TurnoFila>(`/api/tickets/eventos/${eventoId}/fila`, { method: 'POST' })
}

export function consultarFila(eventoId: number) {
  return api<PosicionFila>(`/api/tickets/eventos/${eventoId}/fila`)
}

export function salirDeLaFila(eventoId: number) {
  return api<null>(`/api/tickets/eventos/${eventoId}/fila`, { method: 'DELETE' })
}

export function obtenerReporteEntradas(eventoId: number) {
  return api<ReporteEntradasVendidas>(`/api/tickets/reportes/eventos/${eventoId}/entradas`)
}

export function obtenerReporteOcupacion(eventoId: number) {
  return api<ReporteOcupacion>(`/api/tickets/reportes/eventos/${eventoId}/ocupacion`)
}

export function obtenerReporteRecaudacion(eventoId: number) {
  return api<ReporteRecaudacion>(`/api/tickets/reportes/eventos/${eventoId}/recaudacion`)
}

export function validarAcceso(eventoId: number, body: ValidarAccesoRequest) {
  return api<ResultadoAcceso>(`/api/tickets/eventos/${eventoId}/accesos/validar`, {
    method: 'POST',
    body,
  })
}
