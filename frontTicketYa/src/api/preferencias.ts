import { api } from './http'
import type {
  HistorialCompra,
  Preferencias,
  PreferenciasRequest,
  Recomendacion,
} from './types'

export function obtenerMisPreferencias() {
  return api<Preferencias>(`/api/preferencias/mias`)
}

export function guardarMisPreferencias(body: PreferenciasRequest) {
  return api<Preferencias>(`/api/preferencias/mias`, { method: 'PUT', body })
}

export function obtenerHistorial() {
  return api<HistorialCompra[]>(`/api/preferencias/historial`)
}

export function obtenerRecomendaciones() {
  return api<Recomendacion[]>(`/api/recomendaciones`)
}
