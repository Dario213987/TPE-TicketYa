import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { consultarFila, entrarEnFila, salirDeLaFila } from '../api/tickets'
import { formatFecha } from '../lib/format'
import { Cargando, ErrorBox } from './ui'

export function FilaPanel({
  eventoId,
  onPase,
}: {
  eventoId: number
  onPase: (token: string | null) => void
}) {
  const queryClient = useQueryClient()
  const [enFila, setEnFila] = useState(false)

  const posicion = useQuery({
    queryKey: ['fila', eventoId],
    queryFn: () => consultarFila(eventoId),
    enabled: enFila,
    retry: false,
    refetchInterval: enFila ? 5000 : false,
  })

  const entrar = useMutation({
    mutationFn: () => entrarEnFila(eventoId),
    onSuccess: () => {
      setEnFila(true)
      void queryClient.invalidateQueries({ queryKey: ['fila', eventoId] })
    },
  })

  const salir = useMutation({
    mutationFn: () => salirDeLaFila(eventoId),
    onSuccess: () => {
      setEnFila(false)
      onPase(null)
      queryClient.removeQueries({ queryKey: ['fila', eventoId] })
    },
  })

  useEffect(() => {
    if (posicion.data?.estado === 'HABILITADO' && posicion.data.pase) {
      onPase(posicion.data.pase.token)
    }
  }, [posicion.data, onPase])

  const habilitado = posicion.data?.estado === 'HABILITADO'

  return (
    <section className="panel panel-fila">
      <h2>Fila virtual</h2>
      <p className="nota">
        Este evento es de alta demanda: necesitás un pase para reservar.
      </p>

      {entrar.isError && <ErrorBox error={entrar.error} />}

      {!enFila && (
        <button
          type="button"
          className="btn"
          onClick={() => entrar.mutate()}
          disabled={entrar.isPending}
        >
          {entrar.isPending ? 'Entrando…' : 'Entrar a la fila'}
        </button>
      )}

      {enFila && posicion.isLoading && <Cargando texto="Consultando tu posición…" />}
      {enFila && posicion.isError && !habilitado && (
        <ErrorBox error={posicion.error} onRetry={() => posicion.refetch()} />
      )}

      {enFila && posicion.data && (
        <div className="fila-estado">
          {habilitado ? (
            <>
              <p className="ok">¡Estás habilitado para comprar!</p>
              {posicion.data.pase && (
                <p className="nota">
                  Pase válido hasta {formatFecha(posicion.data.pase.expiraEl)}
                </p>
              )}
            </>
          ) : (
            <>
              <p>
                Tu posición: <strong>{posicion.data.posicion ?? '—'}</strong> de{' '}
                {posicion.data.totalEnFila}
              </p>
              <p className="nota">
                Delante tuyo: {posicion.data.personasDelante ?? 0}. Se actualiza cada 5 segundos.
              </p>
            </>
          )}

          <button
            type="button"
            className="btn secundario"
            onClick={() => salir.mutate()}
            disabled={salir.isPending}
          >
            {salir.isPending ? 'Saliendo…' : 'Salir de la fila'}
          </button>
        </div>
      )}
    </section>
  )
}
