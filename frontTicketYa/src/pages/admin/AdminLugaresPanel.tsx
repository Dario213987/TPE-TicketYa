import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import {
  crearLugar,
  crearSectorNoNumerado,
  crearSectorNumerado,
} from '../../api/lugares'
import type { Lugar } from '../../api/types'
import { ErrorBox } from '../../components/ui'

export function AdminLugaresPanel() {
  const [lugar, setLugar] = useState<Lugar | null>(null)
  const [sectoresCreados, setSectoresCreados] = useState<string[]>([])

  const crear = useMutation({
    mutationFn: crearLugar,
    onSuccess: (nuevo) => {
      setLugar(nuevo)
      setSectoresCreados([])
    },
  })

  const numerado = useMutation({
    mutationFn: (body: {
      nombre: string
      precio: number
      filas: number
      asientosPorFila: number
    }) => crearSectorNumerado(lugar!.id, body),
    onSuccess: (_sector, body) =>
      setSectoresCreados((prev) => [...prev, `Numerado · ${body.nombre}`]),
  })

  const noNumerado = useMutation({
    mutationFn: (body: { nombre: string; precio: number; capacidadMaxima: number }) =>
      crearSectorNoNumerado(lugar!.id, body),
    onSuccess: (_sector, body) =>
      setSectoresCreados((prev) => [...prev, `No numerado · ${body.nombre}`]),
  })

  function enviarLugar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    crear.mutate({
      nombre: String(data.get('nombre') ?? '').trim(),
      tipo: String(data.get('tipo') ?? '').trim(),
      direccion: String(data.get('direccion') ?? '').trim(),
      ciudad: String(data.get('ciudad') ?? '').trim(),
      latitud: Number(data.get('latitud')),
      longitud: Number(data.get('longitud')),
      capacidad: data.get('capacidad') ? Number(data.get('capacidad')) : undefined,
    })
  }

  function enviarNumerado(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    numerado.mutate({
      nombre: String(data.get('nombre') ?? '').trim(),
      precio: Number(data.get('precio')),
      filas: Number(data.get('filas')),
      asientosPorFila: Number(data.get('asientosPorFila')),
    })
    event.currentTarget.reset()
  }

  function enviarNoNumerado(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    noNumerado.mutate({
      nombre: String(data.get('nombre') ?? '').trim(),
      precio: Number(data.get('precio')),
      capacidadMaxima: Number(data.get('capacidadMaxima')),
    })
    event.currentTarget.reset()
  }

  return (
    <>
      <section className="panel">
        <h2>Crear lugar</h2>
        <p className="nota">
          Los lugares y sectores son administrativos. Después cambiá a ORGANIZADOR para crear
          el evento.
        </p>
        <form onSubmit={enviarLugar}>
          <div className="fila-filtros">
            <label className="campo">
              <span>Nombre</span>
              <input name="nombre" required />
            </label>
            <label className="campo">
              <span>Tipo</span>
              <input name="tipo" required placeholder="TEATRO, ESTADIO…" />
            </label>
            <label className="campo">
              <span>Ciudad</span>
              <input name="ciudad" required />
            </label>
            <label className="campo campo-ancho">
              <span>Dirección</span>
              <input name="direccion" required />
            </label>
            <label className="campo">
              <span>Latitud</span>
              <input name="latitud" type="number" step="any" required defaultValue={-38.7183} />
            </label>
            <label className="campo">
              <span>Longitud</span>
              <input name="longitud" type="number" step="any" required defaultValue={-62.2663} />
            </label>
            <label className="campo">
              <span>Capacidad (opcional)</span>
              <input name="capacidad" type="number" min={0} />
            </label>
          </div>
          {crear.isError && <ErrorBox error={crear.error} />}
          <button type="submit" className="btn" disabled={crear.isPending}>
            {crear.isPending ? 'Creando…' : 'Crear lugar'}
          </button>
        </form>
      </section>

      {lugar && (
        <section className="panel">
          <h2>
            Lugar #{lugar.id}: {lugar.nombre}
          </h2>
          <p className="nota">Agregá los sectores del lugar antes de crear un evento.</p>

          <div className="detalle-grid">
            <form onSubmit={enviarNumerado}>
              <h3>Sector numerado</h3>
              <label className="campo">
                <span>Nombre</span>
                <input name="nombre" required />
              </label>
              <label className="campo">
                <span>Precio base</span>
                <input name="precio" type="number" min={0} step="any" required />
              </label>
              <label className="campo">
                <span>Filas</span>
                <input name="filas" type="number" min={1} required />
              </label>
              <label className="campo">
                <span>Asientos por fila</span>
                <input name="asientosPorFila" type="number" min={1} required />
              </label>
              {numerado.isError && <ErrorBox error={numerado.error} />}
              <button type="submit" className="btn" disabled={numerado.isPending}>
                {numerado.isPending ? 'Creando…' : 'Crear sector numerado'}
              </button>
            </form>

            <form onSubmit={enviarNoNumerado}>
              <h3>Sector no numerado</h3>
              <label className="campo">
                <span>Nombre</span>
                <input name="nombre" required />
              </label>
              <label className="campo">
                <span>Precio base</span>
                <input name="precio" type="number" min={0} step="any" required />
              </label>
              <label className="campo">
                <span>Capacidad máxima</span>
                <input name="capacidadMaxima" type="number" min={1} required />
              </label>
              {noNumerado.isError && <ErrorBox error={noNumerado.error} />}
              <button type="submit" className="btn" disabled={noNumerado.isPending}>
                {noNumerado.isPending ? 'Creando…' : 'Crear sector no numerado'}
              </button>
            </form>
          </div>

          {sectoresCreados.length > 0 && (
            <ul className="nota">
              {sectoresCreados.map((sector) => (
                <li key={sector}>Sector creado: {sector}</li>
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  )
}
