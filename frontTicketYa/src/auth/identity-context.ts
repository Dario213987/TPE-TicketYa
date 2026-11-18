import { createContext } from 'react'
import type { Identity } from './storage'

export interface IdentityContextValue {
  identity: Identity | null
  setIdentity: (identity: Identity | null) => void
}

export const IdentityContext = createContext<IdentityContextValue | null>(null)
