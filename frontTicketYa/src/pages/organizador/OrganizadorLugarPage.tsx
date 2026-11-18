import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { obtenerLugar } from '../../api/lugares'
import { useIdentity } from '../../auth/useIdentity'
import { Cargando, ErrorBox, Vacio } from '../../components/ui'
import { formatPrecio } from '../../lib/format'

export function OrganizadorLugarPage() {
  const { lugarId } = useParams()
  const id = Number(lugarId)
  const { identity } = useIdentity()
  const esOrganizador = identity?.role === 'ORGANIZADOR'

  const lugar = useQuery({
    queryKey: ['lugar', id],
    queryFn: () => obtenerLugar(id),
    enabled: esOrganizador && Number.isFinite(id),
  })

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
      <Link to="/organizador/lugares" className="volver">
        ← Volver a lugares
      </Link>

      {lugar.isLoading && <Cargando texto="Cargando lugar…" />}
      {lugar.isError && <ErrorBox error={lugar.error} onRetry={() => lugar.refetch()} />}

      {lugar.data && (
        <>
          <header className="detalle-header sin-portada">
            <span className="tipo">{lugar.data.tipo}</span>
            <h1>{lugar.data.nombre}</h1>
            <p className="fecha">
              {lugar.data.direccion} · {lugar.data.ciudad}
            </p>
          </header>

          <section className="panel">
            <h2>Sectores</h2>
            {(lugar.data.sectores ?? []).length === 0 ? (
              <p className="nota">Todavía no hay sectores.</p>
            ) : (
              <table className="tabla">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Tipo</th>
                    <th>Capacidad</th>
                    <th>Precio base</th>
                  </tr>
                </thead>
                <tbody>
                  {(lugar.data.sectores ?? []).map((sector) => (
                    <tr key={sector.id}>
                      <td>{sector.nombre}</td>
                      <td>{sector.tipoSector === 'NUMERADO' ? 'Numerado' : 'No numerado'}</td>
                      <td>{sector.capacidad ?? sector.capacidadMaxima ?? '—'}</td>
                      <td>{formatPrecio(sector.precio)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <p className="nota">
            Los sectores los crea un administrador. Si necesitás agregar uno, pedíselo a un
            usuario con rol ADMINISTRADOR.
          </p>
        </>
      )}
    </section>
  )
}
