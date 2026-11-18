import type { Rol } from '../api/types'

export interface Identity {
  userId: number
  role: Rol
  nombre: string
}

const STORAGE_KEY = 'ticketya.identity'

export const IDENTIDADES: Identity[] = [
  { userId: 100, role: 'CLIENTE', nombre: 'Ana Cliente' },
  { userId: 7, role: 'ORGANIZADOR', nombre: 'Oscar Organizador' },
  { userId: 1, role: 'ADMINISTRADOR', nombre: 'Ada Admin' },
]

export const ROLES: Rol[] = ['CLIENTE', 'ORGANIZADOR', 'ADMINISTRADOR']

export function readIdentity(): Identity | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return IDENTIDADES[0]
  }
  try {
    const parsed = JSON.parse(raw) as Partial<Identity>
    if (typeof parsed.userId === 'number' && parsed.role && parsed.nombre) {
      return parsed as Identity
    }
  } catch {
    // ignoramos un valor corrupto y volvemos al usuario por defecto
  }
  return IDENTIDADES[0]
}

export function writeIdentity(identity: Identity | null): void {
  if (!identity) {
    localStorage.removeItem(STORAGE_KEY)
    return
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
}
