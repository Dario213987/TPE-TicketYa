import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  actualizarEvento,
  definirPrecioSector,
  obtenerEvento,
  publicarEvento,
} from '../../api/eventos'
import { obtenerLugar } from '../../api/lugares'
import type { Sector, TipoEvento } from '../../api/types'
import { useIdentity } from '../../auth/useIdentity'
import { Cargando, ErrorBox, EstadoBadge, Vacio } from '../../components/ui'
import { formatFecha, formatPrecio } from '../../lib/format'

const TIPOS: TipoEvento[] = [
  'RECITAL',
  'FESTIVAL',
  'TEATRO',
  'DEPORTE',
  'CONFERENCIA',
  'OTRO',
]

function aDatetimeLocal(iso: string | undefined): string {
  return iso ? iso.slice(0, 16) : ''
}

function PrecioRow({
  eventoId,
  sector,
  precioActual,
  onGuardado,
}: {
  eventoId: number
  sector: Sector
  precioActual?: number
  onGuardado: () => void
}) {
  const [valor, setValor] = useState(precioActual ? String(precioActual) : '')

  const guardar = useMutation({
    mutationFn: () => definirPrecioSector(eventoId, sector.id, Number(valor)),
    onSuccess: onGuardado,
  })

  return (
    <tr>
      <td>{sector.nombre}</td>
      <td>{sector.tipoSector === 'NUMERADO' ? 'Numerado' : 'No numerado'}</td>
      <td>{formatPrecio(precioActual)}</td>
      <td>
        <div className="precio-editor">
          <input
            type="number"
            min={1}
            step="any"
            value={valor}
            placeholder="Precio"
            onChange={(event) => setValor(event.target.value)}
          />
          <button
            type="button"
            className="btn secundario"
            disabled={guardar.isPending || !valor}
            onClick={() => guardar.mutate()}
          >
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
        {guardar.isError && <ErrorBox error={guardar.error} />}
      </td>
    </tr>
  )
}

export function OrganizadorEventoPage() {
  const { eventoId } = useParams()
  const id = Number(eventoId)
  const { identity } = useIdentity()
  const esOrganizador = identity?.role === 'ORGANIZADOR'
  const queryClient = useQueryClient()

  const evento = useQuery({
    queryKey: ['evento', id],
    queryFn: () => obtenerEvento(id),
    enabled: esOrganizador && Number.isFinite(id),
  })

  const lugarId = evento.data?.lugar?.id
  const lugar = useQuery({
    queryKey: ['lugar', lugarId],
    queryFn: () => obtenerLugar(lugarId as number),
    enabled: esOrganizador && !!lugarId,
  })

  const invalidar = () => {
    void queryClient.invalidateQueries({ queryKey: ['evento', id] })
    void queryClient.invalidateQueries({ queryKey: ['organizador-eventos'] })
    void queryClient.invalidateQueries({ queryKey: ['eventos'] })
  }

  const publicar = useMutation({
    mutationFn: () => publicarEvento(id),
    onSuccess: invalidar,
  })

  const actualizar = useMutation({
    mutationFn: (body: {
      nombre: string
      descripcion: string
      tipo: TipoEvento
      fecha?: string
    }) => actualizarEvento(id, body),
    onSuccess: invalidar,
  })

  function enviarEdicion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const fecha = String(data.get('fecha') ?? '')
    actualizar.mutate({
      nombre: String(data.get('nombre') ?? '').trim(),
      descripcion: String(data.get('descripcion') ?? '').trim(),
      tipo: data.get('tipo') as TipoEvento,
      fecha: fecha ? (fecha.length === 16 ? `${fecha}:00` : fecha) : undefined,
    })
  }

  if (!esOrganizador) {
    return (
      <section className="pagina">
        <Vacio>
          <strong>Necesitás el rol ORGANIZADOR</strong>
        </Vacio>
      </section>
    )
  }

  if (evento.isLoading) {
    return <Cargando texto="Cargando evento…" />
  }

  if (evento.isError) {
    return (
      <section className="pagina">
        <Link to="/organizador" className="volver">
          ← Volver al panel
        </Link>
        <ErrorBox error={evento.error} onRetry={() => evento.refetch()} />
      </section>
    )
  }

  if (!evento.data) {
    return null
  }

  const detalle = evento.data
  const sectores = lugar.data?.sectores ?? detalle.sectores ?? []
  const precios = detalle.preciosSectores ?? {}
  const esBorrador = detalle.estado === 'BORRADOR'

  return (
    <section className="pagina">
      <Link to="/organizador" className="volver">
        ← Volver al panel
      </Link>

      <header className="detalle-header sin-portada">
        <div className="detalle-meta">
          <span className="tipo">{detalle.tipo}</span>
          <EstadoBadge estado={detalle.estado} />
        </div>
        <h1>{detalle.nombre}</h1>
        <p className="fecha">{formatFecha(detalle.fecha)}</p>
        <p className="nota">
          Lugar: {detalle.lugar?.nombre ?? '—'} · {detalle.lugar?.ciudad ?? '—'}
        </p>
      </header>

      <div className="acciones separadas">
        <Link className="btn secundario" to={`/eventos/${detalle.id}`}>
          Ver ficha pública
        </Link>
        <button
          type="button"
          className="btn"
          disabled={!esBorrador || publicar.isPending}
          onClick={() => publicar.mutate()}
        >
          {publicar.isPending ? 'Publicando…' : esBorrador ? 'Publicar evento' : 'Evento publicado'}
        </button>
      </div>
      {publicar.isError && <ErrorBox error={publicar.error} />}

      <section className="panel">
        <h2>Precios por sector</h2>
        {lugar.isLoading && sectores.length === 0 && <Cargando texto="Cargando sectores…" />}
        {lugar.isError && <ErrorBox error={lugar.error} onRetry={() => lugar.refetch()} />}
        {sectores.length === 0 && !lugar.isLoading && !lugar.isError ? (
          <p className="nota">
            El lugar del evento no tiene sectores. Un administrador debe crear los sectores
            desde <Link to="/admin">Administración</Link>.
          </p>
        ) : sectores.length > 0 ? (
          <table className="tabla">
            <thead>
              <tr>
                <th>Sector</th>
                <th>Tipo</th>
                <th>Precio actual</th>
                <th>Definir precio</th>
              </tr>
            </thead>
            <tbody>
              {sectores.map((sector) => (
                <PrecioRow
                  key={sector.id}
                  eventoId={detalle.id}
                  sector={sector}
                  precioActual={precios[String(sector.id)]}
                  onGuardado={invalidar}
                />
              ))}
            </tbody>
          </table>
        ) : null}
        <p className="nota">
          Todos los sectores deben tener precio antes de publicar el evento.
        </p>
      </section>

      <form
        className="panel"
        key={`${detalle.id}-${detalle.nombre}-${detalle.tipo}-${detalle.fecha}`}
        onSubmit={enviarEdicion}
      >
        <h2>Editar datos</h2>
        <label className="campo">
          <span>Nombre</span>
          <input name="nombre" required maxLength={200} defaultValue={detalle.nombre} />
        </label>
        <label className="campo">
          <span>Descripción</span>
          <textarea
            name="descripcion"
            rows={3}
            maxLength={255}
            defaultValue={detalle.descripcion ?? ''}
          />
        </label>
        <div className="fila-filtros">
          <label className="campo">
            <span>Tipo</span>
            <select name="tipo" defaultValue={detalle.tipo}>
              {TIPOS.map((tipo) => (
                <option key={tipo} value={tipo}>
                  {tipo}
                </option>
              ))}
            </select>
          </label>
          <label className="campo">
            <span>Fecha y hora {esBorrador ? '' : '(bloqueada)'}</span>
            <input
              name="fecha"
              type="datetime-local"
              defaultValue={aDatetimeLocal(detalle.fecha)}
              disabled={!esBorrador}
            />
          </label>
        </div>
        {actualizar.isError && <ErrorBox error={actualizar.error} />}
        {actualizar.isSuccess && <p className="ok">Cambios guardados.</p>}
        <button type="submit" className="btn" disabled={actualizar.isPending}>
          {actualizar.isPending ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </form>
    </section>
  )
}
