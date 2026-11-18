import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { listarLugares } from '../../api/lugares'
import { useIdentity } from '../../auth/useIdentity'
import { Cargando, ErrorBox, Vacio } from '../../components/ui'

export function OrganizadorLugaresPage() {
  const { identity } = useIdentity()
  const esOrganizador = identity?.role === 'ORGANIZADOR'

  const lugares = useQuery({
    queryKey: ['lugares', identity?.userId],
    queryFn: () => listarLugares(undefined, 0, 50),
    enabled: esOrganizador,
  })

  if (!esOrganizador) {
    return (
      <section className="pagina">
        <h1>Lugares</h1>
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
          <Link to="/organizador" className="volver">
            ← Volver al panel
          </Link>
          <h1>Lugares</h1>
          <p className="nota">
            Los lugares y sectores los crea un administrador. Acá podés consultarlos para
            asociarlos a tus eventos.
          </p>
        </div>
      </div>

      {lugares.isLoading && <Cargando texto="Cargando lugares…" />}
      {lugares.isError && <ErrorBox error={lugares.error} onRetry={() => lugares.refetch()} />}
      {lugares.data && (
        <div className="grid-eventos">
          {lugares.data.content.map((lugar) => (
            <Link key={lugar.id} to={`/organizador/lugares/${lugar.id}`} className="event-card">
              <div className="event-card-top">
                <span className="tipo">{lugar.tipo}</span>
              </div>
              <h3>{lugar.nombre}</h3>
              <p className="fecha">{lugar.ciudad}</p>
              <p className="nota">{lugar.direccion}</p>
              <span className="ver">Administrar sectores →</span>
            </Link>
          ))}
        </div>
      )}
      {lugares.data && lugares.data.content.length === 0 && (
        <Vacio>
          <strong>No hay lugares cargados</strong>
          <p>Creá el primero con el botón “Nuevo lugar”.</p>
        </Vacio>
      )}
    </section>
  )
}
