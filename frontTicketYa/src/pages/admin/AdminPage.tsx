import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelarEvento,
  obtenerEvento,
  reactivarEvento,
  suspenderEvento,
} from '../../api/eventos'
import { listarEventos } from '../../api/eventos'
import type { EstadoEvento } from '../../api/types'
import { useIdentity } from '../../auth/useIdentity'
import { Cargando, ErrorBox, EstadoBadge, Vacio } from '../../components/ui'
import { formatFecha } from '../../lib/format'
import { AdminLugaresPanel } from './AdminLugaresPanel'

function AccionesEvento({
  eventoId,
  estado,
  onCambio,
}: {
  eventoId: number
  estado: EstadoEvento
  onCambio: () => void
}) {
  const queryClient = useQueryClient()

  const invalidar = () => {
    onCambio()
    void queryClient.invalidateQueries({ queryKey: ['eventos'] })
    void queryClient.invalidateQueries({ queryKey: ['evento', eventoId] })
  }

  const suspender = useMutation({
    mutationFn: () => suspenderEvento(eventoId),
    onSuccess: invalidar,
  })
  const reactivar = useMutation({
    mutationFn: () => reactivarEvento(eventoId),
    onSuccess: invalidar,
  })
  const cancelar = useMutation({
    mutationFn: () => cancelarEvento(eventoId),
    onSuccess: invalidar,
  })

  const error = suspender.error ?? reactivar.error ?? cancelar.error
  const puedeSuspender = estado === 'PUBLICADO' || estado === 'AGOTADO'
  const puedeReactivar = estado === 'SUSPENDIDO'
  const puedeCancelar =
    estado === 'PUBLICADO' || estado === 'AGOTADO' || estado === 'SUSPENDIDO'

  if (!puedeSuspender && !puedeReactivar && !puedeCancelar) {
    return null
  }

  return (
    <div className="acciones">
      {puedeSuspender && (
        <button
          type="button"
          className="btn secundario"
          disabled={suspender.isPending}
          onClick={() => suspender.mutate()}
        >
          Suspender
        </button>
      )}
      {puedeReactivar && (
        <button
          type="button"
          className="btn secundario"
          disabled={reactivar.isPending}
          onClick={() => reactivar.mutate()}
        >
          Reactivar
        </button>
      )}
      {puedeCancelar && (
        <button
          type="button"
          className="btn peligro"
          disabled={cancelar.isPending}
          onClick={() => {
            if (confirm('¿Cancelar el evento y disparar el reintegro masivo?')) {
              cancelar.mutate()
            }
          }}
        >
          Cancelar
        </button>
      )}
      {error && <ErrorBox error={error} />}
    </div>
  )
}

export function AdminPage() {
  const { identity } = useIdentity()
  const esAdmin = identity?.role === 'ADMINISTRADOR'
  const [idGestion, setIdGestion] = useState('')
  const [idConsulta, setIdConsulta] = useState<number | null>(null)

  const eventos = useQuery({
    queryKey: ['admin-eventos'],
    queryFn: () => listarEventos({ size: 50 }),
    enabled: esAdmin,
  })

  const consultado = useQuery({
    queryKey: ['evento', idConsulta],
    queryFn: () => obtenerEvento(idConsulta as number),
    enabled: esAdmin && idConsulta !== null,
    retry: false,
  })

  if (!esAdmin) {
    return (
      <section className="pagina">
        <h1>Administración</h1>
        <Vacio>
          <strong>Necesitás el rol ADMINISTRADOR</strong>
          <p>Cambiá el usuario de prueba en la barra superior.</p>
        </Vacio>
      </section>
    )
  }

  return (
    <section className="pagina">
      <h1>Administración</h1>

      <AdminLugaresPanel />

      <section className="panel">
        <h2>Gestionar un evento por id</h2>
        <p className="nota">
          Útil para eventos suspendidos o cancelados que no aparecen en el listado público.
        </p>
        <div className="acciones">
          <input
            type="number"
            min={1}
            value={idGestion}
            placeholder="Id del evento"
            onChange={(event) => setIdGestion(event.target.value)}
          />
          <button
            type="button"
            className="btn secundario"
            disabled={!idGestion}
            onClick={() => setIdConsulta(Number(idGestion))}
          >
            Consultar
          </button>
        </div>

        {consultado.isLoading && <Cargando texto="Consultando evento…" />}
        {consultado.isError && (
          <ErrorBox error={consultado.error} onRetry={() => consultado.refetch()} />
        )}
        {consultado.data && (
          <div className="gestion-evento">
            <div>
              <strong>{consultado.data.nombre}</strong>
              <span className="nota">
                {' '}
                · {consultado.data.tipo} · {formatFecha(consultado.data.fecha)}
              </span>
            </div>
            <EstadoBadge estado={consultado.data.estado} />
            <AccionesEvento
              eventoId={consultado.data.id}
              estado={consultado.data.estado}
              onCambio={() => void consultado.refetch()}
            />
          </div>
        )}
      </section>

      <h2 className="subtitulo">Eventos visibles</h2>
      {eventos.isLoading && <Cargando texto="Cargando eventos…" />}
      {eventos.isError && <ErrorBox error={eventos.error} onRetry={() => eventos.refetch()} />}
      {eventos.data && eventos.data.content.length === 0 && (
        <Vacio>
          <strong>No hay eventos visibles</strong>
        </Vacio>
      )}
      {eventos.data && (
        <div className="lista-admin">
          {eventos.data.content.map((evento) => (
            <article key={evento.id} className="admin-fila">
              <div className="admin-fila-info">
                <span className="tipo">{evento.tipo}</span>
                <strong>{evento.nombre}</strong>
                <span className="nota">{formatFecha(evento.fecha)}</span>
              </div>
              <EstadoBadge estado={evento.estado} />
              <AccionesEvento
                eventoId={evento.id}
                estado={evento.estado}
                onCambio={() => void eventos.refetch()}
              />
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
