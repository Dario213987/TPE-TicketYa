import { api, buildQuery } from './http'
import type {
  Devolucion,
  DevolucionFiltros,
  Paged,
  SolicitudDevolucionRequest,
} from './types'

export function solicitarDevolucion(body: SolicitudDevolucionRequest) {
  return api<Devolucion>(`/api/devoluciones`, { method: 'POST', body })
}

export function listarDevoluciones(filtros: DevolucionFiltros = {}) {
  return api<Paged<Devolucion>>(
    `/api/devoluciones${buildQuery({
      estado: filtros.estado,
      clienteId: filtros.clienteId,
      eventoId: filtros.eventoId,
      page: filtros.page,
      size: filtros.size,
      sort: filtros.sort,
    })}`,
  )
}

export function obtenerDevolucion(id: number) {
  return api<Devolucion>(`/api/devoluciones/${id}`)
}

export function obtenerDevolucionPorTicket(ticketId: number) {
  return api<Devolucion>(`/api/devoluciones/ticket/${ticketId}`)
}
