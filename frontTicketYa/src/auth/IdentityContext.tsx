import { useMemo, useState, type ReactNode } from 'react'
import { IdentityContext, type IdentityContextValue } from './identity-context'
import { readIdentity, writeIdentity, type Identity } from './storage'

export function IdentityProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentityState] = useState<Identity | null>(() => readIdentity())

  const value = useMemo<IdentityContextValue>(
    () => ({
      identity,
      setIdentity: (next) => {
        writeIdentity(next)
        setIdentityState(next)
      },
    }),
    [identity],
  )

  return <IdentityContext.Provider value={value}>{children}</IdentityContext.Provider>
}
