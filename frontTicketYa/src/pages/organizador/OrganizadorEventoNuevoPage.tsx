import type { FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { crearEvento } from '../../api/eventos'
import { listarLugares } from '../../api/lugares'
import type { TipoEvento } from '../../api/types'
import { useIdentity } from '../../auth/useIdentity'
import { Cargando, ErrorBox, Vacio } from '../../components/ui'

const TIPOS: TipoEvento[] = [
  'RECITAL',
  'FESTIVAL',
  'TEATRO',
  'DEPORTE',
  'CONFERENCIA',
  'OTRO',
]

export function OrganizadorEventoNuevoPage() {
  const { identity } = useIdentity()
  const esOrganizador = identity?.role === 'ORGANIZADOR'
  const navigate = useNavigate()

  const lugares = useQuery({
    queryKey: ['lugares', identity?.userId],
    queryFn: () => listarLugares(undefined, 0, 100),
    enabled: esOrganizador,
  })

  const crear = useMutation({
    mutationFn: crearEvento,
    onSuccess: (evento) => navigate(`/organizador/eventos/${evento.id}`),
  })

  function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const fecha = String(data.get('fecha') ?? '')
    const participantes = String(data.get('participantes') ?? '')
      .split(',')
      .map((nombre) => nombre.trim())
      .filter(Boolean)
      .map((nombre) => ({ nombre }))

    crear.mutate({
      nombre: String(data.get('nombre') ?? '').trim(),
      descripcion: String(data.get('descripcion') ?? '').trim(),
      tipo: data.get('tipo') as TipoEvento,
      fecha: fecha.length === 16 ? `${fecha}:00` : fecha,
      lugarId: Number(data.get('lugarId')),
      altaDemanda: data.get('altaDemanda') === 'on',
      participantes: participantes.length > 0 ? participantes : undefined,
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

  return (
    <section className="pagina">
      <Link to="/organizador" className="volver">
        ← Volver al panel
      </Link>
      <h1>Nuevo evento</h1>

      {lugares.isLoading && <Cargando texto="Cargando lugares…" />}
      {lugares.isError && <ErrorBox error={lugares.error} onRetry={() => lugares.refetch()} />}

      {lugares.data && lugares.data.content.length === 0 && (
        <Vacio>
          <strong>Primero hace falta un lugar</strong>
          <p>
            Un evento necesita un lugar con sectores, y los crea un usuario con rol
            ADMINISTRADOR. Cambiá el usuario de prueba en la barra superior para crearlo.
          </p>
        </Vacio>
      )}

      {lugares.data && lugares.data.content.length > 0 && (
        <form className="panel" onSubmit={enviar}>
          <label className="campo">
            <span>Nombre</span>
            <input name="nombre" required maxLength={200} />
          </label>
          <label className="campo">
            <span>Descripción</span>
            <textarea name="descripcion" required maxLength={255} rows={3} />
          </label>
          <div className="fila-filtros">
            <label className="campo">
              <span>Tipo</span>
              <select name="tipo" defaultValue="RECITAL">
                {TIPOS.map((tipo) => (
                  <option key={tipo} value={tipo}>
                    {tipo}
                  </option>
                ))}
              </select>
            </label>
            <label className="campo">
              <span>Fecha y hora</span>
              <input name="fecha" type="datetime-local" required />
            </label>
            <label className="campo">
              <span>Lugar</span>
              <select name="lugarId" required>
                {lugares.data.content.map((lugar) => (
                  <option key={lugar.id} value={lugar.id}>
                    {lugar.nombre} · {lugar.ciudad}
                  </option>
                ))}
              </select>
            </label>
            <label className="campo campo-ancho">
              <span>Participantes (nombres separados por coma)</span>
              <input name="participantes" placeholder="Ej.: Soda Stereo, Café Tacvba" />
            </label>
          </div>
          <label className="check">
            <input type="checkbox" name="altaDemanda" />
            Marcar como evento de alta demanda (fila virtual)
          </label>

          {crear.isError && <ErrorBox error={crear.error} />}
          <button type="submit" className="btn" disabled={crear.isPending}>
            {crear.isPending ? 'Creando…' : 'Crear borrador'}
          </button>
        </form>
      )}
    </section>
  )
}
