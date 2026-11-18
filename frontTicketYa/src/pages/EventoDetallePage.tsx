import { useCallback, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { obtenerEvento } from '../api/eventos'
import { consultarDisponibilidad } from '../api/tickets'
import { esNoEncontrado } from '../api/http'
import { useIdentity } from '../auth/useIdentity'
import { FilaPanel } from '../components/FilaPanel'
import { ReservationPanel } from '../components/ReservationPanel'
import { Cargando, ErrorBox, EstadoBadge } from '../components/ui'
import { formatFecha, formatPrecio } from '../lib/format'
import { IMAGEN_TIPO } from '../lib/eventosVisuales'

export function EventoDetallePage() {
  const { eventoId } = useParams()
  const id = Number(eventoId)
  const { identity } = useIdentity()
  const [pase, setPase] = useState<{ userId: number | null; token: string | null }>({
    userId: null,
    token: null,
  })
  const paseToken = pase.userId === (identity?.userId ?? null) ? pase.token : null
  const onPase = useCallback(
    (token: string | null) => setPase({ userId: identity?.userId ?? null, token }),
    [identity?.userId],
  )

  const evento = useQuery({
    queryKey: ['evento', id],
    queryFn: () => obtenerEvento(id),
    enabled: Number.isFinite(id),
  })

  const disponibilidad = useQuery({
    queryKey: ['disponibilidad', id],
    queryFn: () => consultarDisponibilidad(id),
    enabled: Number.isFinite(id),
    retry: false,
  })

  if (evento.isLoading) {
    return <Cargando texto="Cargando evento…" />
  }

  if (evento.isError) {
    return (
      <section className="pagina">
        <Link to="/" className="volver">
          ← Volver al catálogo
        </Link>
        <ErrorBox error={evento.error} onRetry={() => evento.refetch()} />
      </section>
    )
  }

  if (!evento.data) {
    return null
  }

  const detalle = evento.data
  const precios = detalle.preciosSectores ?? {}
  const esCliente = identity?.role === 'CLIENTE'
  const requierePase = Boolean(disponibilidad.data?.altaDemanda ?? detalle.altaDemanda)

  return (
    <section className="pagina">
      <Link to="/" className="volver">
        ← Volver al catálogo
      </Link>

      <img className="detalle-portada" src={IMAGEN_TIPO[detalle.tipo]} alt="" />

      <header className="detalle-header">
        <div className="detalle-meta">
          <span className="tipo">{detalle.tipo}</span>
          <EstadoBadge estado={detalle.estado} />
          {detalle.altaDemanda && <span className="badge alta-demanda">Alta demanda</span>}
        </div>
        <h1>{detalle.nombre}</h1>
        <p className="fecha">{formatFecha(detalle.fecha)}</p>
      </header>

      <div className="detalle-grid">
        <div className="detalle-principal">
          <section className="panel">
            <h2>Descripción</h2>
            <p>{detalle.descripcion || 'Sin descripción.'}</p>
          </section>

          <section className="panel">
            <h2>Sectores y precios</h2>
            {(detalle.sectores ?? []).length === 0 ? (
              <p className="nota">El lugar todavía no tiene sectores definidos.</p>
            ) : (
              <table className="tabla">
                <thead>
                  <tr>
                    <th>Sector</th>
                    <th>Tipo</th>
                    <th>Capacidad</th>
                    <th>Precio</th>
                  </tr>
                </thead>
                <tbody>
                  {(detalle.sectores ?? []).map((sector) => (
                    <tr key={sector.id}>
                      <td>{sector.nombre}</td>
                      <td>{sector.tipoSector === 'NUMERADO' ? 'Numerado' : 'No numerado'}</td>
                      <td>{sector.capacidad ?? sector.capacidadMaxima ?? '—'}</td>
                      <td>{formatPrecio(precios[String(sector.id)])}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className="panel">
            <h2>Disponibilidad</h2>
            {disponibilidad.isLoading && <Cargando texto="Consultando disponibilidad…" />}
            {disponibilidad.isError &&
              (esNoEncontrado(disponibilidad.error) ? (
                <p className="nota">La disponibilidad todavía no fue publicada.</p>
              ) : (
                <ErrorBox
                  error={disponibilidad.error}
                  onRetry={() => disponibilidad.refetch()}
                />
              ))}
            {disponibilidad.data && (
              <table className="tabla">
                <thead>
                  <tr>
                    <th>Sector</th>
                    <th>Disponibles</th>
                    <th>Capacidad</th>
                  </tr>
                </thead>
                <tbody>
                  {disponibilidad.data.sectores.map((sector) => {
                    const nombre =
                      (detalle.sectores ?? []).find((item) => item.id === sector.sectorId)?.nombre ??
                      `Sector ${sector.sectorId}`
                    return (
                      <tr key={sector.sectorId}>
                        <td>{nombre}</td>
                        <td>
                          <strong>{sector.disponibles}</strong>
                        </td>
                        <td>{sector.capacidadTotal}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </section>
        </div>

        <aside className="detalle-lateral">
          <section className="panel">
            <h2>Lugar</h2>
            {detalle.lugar ? (
              <>
                <p className="destacado">{detalle.lugar.nombre}</p>
                <p>{detalle.lugar.direccion}</p>
                <p className="nota">
                  {detalle.lugar.ciudad} · {detalle.lugar.tipo}
                </p>
              </>
            ) : (
              <p className="nota">Sin lugar asociado.</p>
            )}
          </section>

          <section className="panel">
            <h2>Participantes</h2>
            {(detalle.participantes ?? []).length === 0 ? (
              <p className="nota">Sin participantes informados.</p>
            ) : (
              <ul className="chips">
                {(detalle.participantes ?? []).map((participante) => (
                  <li key={participante.id} className="chip">
                    {participante.nombre}
                  </li>
                ))}
              </ul>
            )}
          </section>

          {esCliente && requierePase && disponibilidad.data && (
            <FilaPanel
              key={`${identity?.userId}-fila`}
              eventoId={id}
              onPase={onPase}
            />
          )}

          {esCliente && disponibilidad.data && (
            <ReservationPanel
              key={`${identity?.userId}-reserva`}
              eventoId={id}
              disponibilidad={disponibilidad.data}
              paseRequerido={requierePase}
              paseToken={paseToken}
            />
          )}

          {!esCliente && (
            <section className="panel">
              <h2>Comprar entradas</h2>
              <p className="nota">Cambiá el usuario de prueba a rol CLIENTE para comprar.</p>
            </section>
          )}
        </aside>
      </div>
    </section>
  )
}
