import { api, buildQuery } from './http'
import type {
  Lugar,
  LugarRequest,
  Paged,
  Sector,
  SectorNoNumeradoRequest,
  SectorNumeradoRequest,
} from './types'

export function listarLugares(ciudad?: string, page = 0, size = 20, sort?: string) {
  return api<Paged<Lugar>>(`/api/lugares${buildQuery({ ciudad, page, size, sort })}`)
}

export function obtenerLugar(id: number) {
  return api<Lugar>(`/api/lugares/${id}`)
}

export function crearLugar(body: LugarRequest) {
  return api<Lugar>(`/api/lugares`, { method: 'POST', body })
}

export function crearSectorNumerado(lugarId: number, body: SectorNumeradoRequest) {
  return api<Sector>(`/api/lugares/${lugarId}/sectores/numerados`, { method: 'POST', body })
}

export function crearSectorNoNumerado(lugarId: number, body: SectorNoNumeradoRequest) {
  return api<Sector>(`/api/lugares/${lugarId}/sectores/no-numerados`, { method: 'POST', body })
}
