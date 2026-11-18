import { useContext } from 'react'
import { IdentityContext, type IdentityContextValue } from './identity-context'

export function useIdentity(): IdentityContextValue {
  const context = useContext(IdentityContext)
  if (!context) {
    throw new Error('useIdentity debe usarse dentro de IdentityProvider')
  }
  return context
}
