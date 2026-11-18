import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { listarEventos } from '../../api/eventos'
import { useIdentity } from '../../auth/useIdentity'
import { Cargando, ErrorBox, EstadoBadge, Vacio } from '../../components/ui'
import { formatFecha } from '../../lib/format'

export function OrganizadorPage() {
  const { identity } = useIdentity()
  const esOrganizador = identity?.role === 'ORGANIZADOR'
  const navigate = useNavigate()
  const [idAbierto, setIdAbierto] = useState('')

  const eventos = useQuery({
    queryKey: ['organizador-eventos', identity?.userId],
    queryFn: () =>
      listarEventos({ organizadorId: identity?.userId ?? '', size: 50 }),
    enabled: esOrganizador && identity !== null,
  })

  if (!esOrganizador) {
    return (
      <section className="pagina">
        <h1>Panel del organizador</h1>
        <Vacio>
          <strong>Necesitás el rol ORGANIZADOR</strong>
          <p>Cambiá el usuario de prueba en la barra superior.</p>
        </Vacio>
      </section>
    )
  }

  return (
    <section className="pagina">
      <div className="titulo-con-acciones">
        <div>
          <h1>Panel del organizador</h1>
          <p className="nota">Organizador #{identity?.userId}</p>
        </div>
        <div className="acciones">
          <Link className="btn secundario" to="/organizador/lugares">
            Lugares y sectores
          </Link>
          <Link className="btn" to="/organizador/eventos/nuevo">
            Nuevo evento
          </Link>
        </div>
      </div>

      <section className="panel">
        <h2>Abrir un evento por id</h2>
        <p className="nota">
          El listado público solo muestra eventos publicados o agotados. Para editar un borrador,
          abrilo por su id.
        </p>
        <div className="acciones">
          <input
            type="number"
            min={1}
            value={idAbierto}
            placeholder="Id del evento"
            onChange={(event) => setIdAbierto(event.target.value)}
          />
          <button
            type="button"
            className="btn secundario"
            disabled={!idAbierto}
            onClick={() => navigate(`/organizador/eventos/${idAbierto}`)}
          >
            Abrir
          </button>
        </div>
      </section>

      <h2 className="subtitulo">Eventos publicados</h2>
      {eventos.isLoading && <Cargando texto="Cargando eventos…" />}
      {eventos.isError && <ErrorBox error={eventos.error} onRetry={() => eventos.refetch()} />}
      {eventos.data && eventos.data.content.length === 0 && (
        <Vacio>
          <strong>No tenés eventos publicados</strong>
          <p>Creá uno nuevo o abrí un borrador por id.</p>
        </Vacio>
      )}
      {eventos.data && eventos.data.content.length > 0 && (
        <div className="grid-eventos">
          {eventos.data.content.map((evento) => (
            <Link
              key={evento.id}
              to={`/organizador/eventos/${evento.id}`}
              className="event-card"
            >
              <div className="event-card-top">
                <span className="tipo">{evento.tipo}</span>
                <EstadoBadge estado={evento.estado} />
              </div>
              <h3>{evento.nombre}</h3>
              <p className="fecha">{formatFecha(evento.fecha)}</p>
              <span className="ver">Gestionar →</span>
            </Link>
          ))}
        </div>
      )}
    </section>
  )
}
