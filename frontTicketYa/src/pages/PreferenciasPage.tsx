import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  guardarMisPreferencias,
  obtenerHistorial,
  obtenerMisPreferencias,
} from '../api/preferencias'
import type { Preferencias, TipoEvento } from '../api/types'
import { useIdentity } from '../auth/useIdentity'
import { Cargando, ErrorBox, Vacio } from '../components/ui'
import { formatFecha } from '../lib/format'

const TIPOS: TipoEvento[] = [
  'RECITAL',
  'FESTIVAL',
  'TEATRO',
  'DEPORTE',
  'CONFERENCIA',
  'OTRO',
]

function FormularioPreferencias({ inicial }: { inicial: Preferencias }) {
  const queryClient = useQueryClient()
  const [tipos, setTipos] = useState<TipoEvento[]>(inicial.tiposFavoritos)
  const [artistas, setArtistas] = useState(inicial.artistasFavoritos.join(', '))
  const [ciudad, setCiudad] = useState(inicial.ciudad ?? '')

  const guardar = useMutation({
    mutationFn: () =>
      guardarMisPreferencias({
        tiposFavoritos: tipos,
        artistasFavoritos: artistas
          .split(',')
          .map((item) => Number(item.trim()))
          .filter((item) => Number.isFinite(item) && item > 0),
        ciudad: ciudad.trim(),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['preferencias'] })
    },
  })

  function alternarTipo(tipo: TipoEvento) {
    setTipos((actuales) =>
      actuales.includes(tipo)
        ? actuales.filter((item) => item !== tipo)
        : [...actuales, tipo],
    )
  }

  function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    guardar.mutate()
  }

  return (
    <form className="panel" onSubmit={enviar}>
      <h2>Mis preferencias</h2>

      <fieldset className="grupo-tipos">
        <legend>Tipos de evento favoritos</legend>
        {TIPOS.map((tipo) => (
          <label key={tipo} className="check">
            <input
              type="checkbox"
              checked={tipos.includes(tipo)}
              onChange={() => alternarTipo(tipo)}
            />
            {tipo}
          </label>
        ))}
      </fieldset>

      <label className="campo">
        <span>Artistas favoritos (ids de participante, separados por coma)</span>
        <input
          type="text"
          value={artistas}
          placeholder="Ej.: 1, 4, 10"
          onChange={(event) => setArtistas(event.target.value)}
        />
      </label>

      <label className="campo">
        <span>Ciudad</span>
        <input
          type="text"
          value={ciudad}
          required
          placeholder="Ej.: Tandil"
          onChange={(event) => setCiudad(event.target.value)}
        />
      </label>

      {guardar.isError && <ErrorBox error={guardar.error} />}
      {guardar.isSuccess && <p className="ok">Preferencias guardadas.</p>}

      <button type="submit" className="btn" disabled={guardar.isPending}>
        {guardar.isPending ? 'Guardando…' : 'Guardar preferencias'}
      </button>
    </form>
  )
}

export function PreferenciasPage() {
  const { identity } = useIdentity()
  const esCliente = identity?.role === 'CLIENTE'

  const preferencias = useQuery({
    queryKey: ['preferencias', identity?.userId],
    queryFn: obtenerMisPreferencias,
    enabled: esCliente,
  })

  const historial = useQuery({
    queryKey: ['historial', identity?.userId],
    queryFn: obtenerHistorial,
    enabled: esCliente,
  })

  if (!esCliente) {
    return (
      <section className="pagina">
        <h1>Preferencias</h1>
        <Vacio>
          <strong>Necesitás el rol CLIENTE</strong>
          <p>Cambiá el usuario de prueba en la barra superior.</p>
        </Vacio>
      </section>
    )
  }

  return (
    <section className="pagina">
      <h1>Preferencias</h1>

      {preferencias.isLoading && <Cargando texto="Cargando preferencias…" />}
      {preferencias.isError && (
        <ErrorBox error={preferencias.error} onRetry={() => preferencias.refetch()} />
      )}
      {preferencias.data && (
        <FormularioPreferencias
          key={`${identity?.userId}-${preferencias.data.ciudad ?? ''}`}
          inicial={preferencias.data}
        />
      )}

      <section className="panel">
        <h2>Historial de compras y asistencias</h2>
        {historial.isLoading && <Cargando texto="Cargando historial…" />}
        {historial.isError && (
          <ErrorBox error={historial.error} onRetry={() => historial.refetch()} />
        )}
        {historial.data && historial.data.length === 0 && (
          <p className="nota">Todavía no hay compras registradas.</p>
        )}
        {historial.data && historial.data.length > 0 && (
          <ul className="lista-historial">
            {historial.data.map((item, index) => (
              <li key={`${item.eventoId}-${index}`}>
                <div>
                  <strong>Evento #{item.eventoId}</strong>
                  <span className="nota">
                    {item.tipo ?? 'Sin tipo'} · {item.ciudad ?? 'Sin ciudad'} ·{' '}
                    {formatFecha(item.fecha)}
                  </span>
                </div>
                <div className="marcas">
                  <span className={`badge ${item.comprado ? 'ok' : 'apagado'}`}>
                    {item.comprado ? 'Comprado' : 'No comprado'}
                  </span>
                  <span className={`badge ${item.usado ? 'ok' : 'apagado'}`}>
                    {item.usado ? 'Asistió' : 'Sin asistencia'}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  )
}
