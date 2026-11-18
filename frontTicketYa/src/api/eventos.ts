import { api, buildQuery } from './http'
import type {
  EventoBorradorRequest,
  EventoDetalle,
  EventoEstado,
  EventoFiltros,
  EventoResumen,
  EventoUpdateRequest,
  Paged,
  PrecioSectorResponse,
} from './types'

export function listarEventos(filtros: EventoFiltros) {
  return api<Paged<EventoResumen>>(
    `/api/eventos${buildQuery({
      tipo: filtros.tipo,
      organizadorId: filtros.organizadorId,
      participante: filtros.participante,
      participanteId: filtros.participanteId,
      ciudad: filtros.ciudad,
      desde: filtros.desde,
      hasta: filtros.hasta,
      fechaDesde: filtros.fechaDesde,
      fechaHasta: filtros.fechaHasta,
      orden: filtros.orden,
      sort: filtros.sort,
      page: filtros.page,
      size: filtros.size,
    })}`,
  )
}

export function obtenerEvento(eventoId: number) {
  return api<EventoDetalle>(`/api/eventos/${eventoId}`)
}

export function crearEvento(body: EventoBorradorRequest) {
  return api<EventoDetalle>(`/api/eventos`, { method: 'POST', body })
}

export function actualizarEvento(eventoId: number, body: EventoUpdateRequest) {
  return api<EventoDetalle>(`/api/eventos/${eventoId}`, { method: 'PUT', body })
}

export function eliminarEvento(eventoId: number) {
  return api<null>(`/api/eventos/${eventoId}`, { method: 'DELETE' })
}

export function publicarEvento(eventoId: number) {
  return api<EventoEstado>(`/api/eventos/${eventoId}/publicar`, { method: 'PUT' })
}

export function cancelarEvento(eventoId: number) {
  return api<EventoEstado>(`/api/eventos/${eventoId}/cancelar`, { method: 'POST' })
}

export function suspenderEvento(eventoId: number) {
  return api<EventoEstado>(`/api/eventos/${eventoId}/suspender`, { method: 'PUT' })
}

export function reactivarEvento(eventoId: number) {
  return api<EventoEstado>(`/api/eventos/${eventoId}/reactivar`, { method: 'PUT' })
}

export function definirPrecioSector(eventoId: number, sectorId: number, precio: number) {
  return api<PrecioSectorResponse>(`/api/eventos/${eventoId}/sectores/${sectorId}/precio`, {
    method: 'PUT',
    body: { precio },
  })
}
