import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="pagina">
      <h1>Página no encontrada</h1>
      <p className="nota">La ruta que buscás no existe.</p>
      <Link className="btn" to="/">
        Volver al catálogo
      </Link>
    </section>
  )
}
