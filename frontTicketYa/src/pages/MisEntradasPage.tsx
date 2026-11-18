import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listarMisEntradas, obtenerQr } from '../api/tickets'
import type { MiEntrada } from '../api/types'
import { useIdentity } from '../auth/useIdentity'
import { Cargando, ErrorBox, Pagination, TicketBadge, Vacio } from '../components/ui'
import { formatFecha, formatPrecio } from '../lib/format'

function EntradaCard({ entrada }: { entrada: MiEntrada }) {
  const [abrir, setAbrir] = useState(false)
  const qr = useQuery({
    queryKey: ['qr', entrada.id],
    queryFn: () => obtenerQr(entrada.id),
    enabled: abrir,
    retry: false,
  })

  const vigente = entrada.estado === 'EMITIDO' || entrada.estado === 'VENDIDO'

  return (
    <article className="tarjeta-entrada">
      <div className="tarjeta-entrada-top">
        <span className="tipo">Evento #{entrada.eventoId}</span>
        <TicketBadge estado={entrada.estado} />
      </div>
      <p>
        Sector {entrada.sectorId}
        {entrada.fila ? ` · Fila ${entrada.fila}` : ''}
        {entrada.numero ? ` · Asiento ${entrada.numero}` : ''}
      </p>
      <p className="nota">
        Emitida {formatFecha(entrada.fechaEmision)} · {formatPrecio(entrada.precio)}
      </p>

      {vigente && (
        <>
          {!abrir ? (
            <button type="button" className="btn secundario" onClick={() => setAbrir(true)}>
              Ver QR
            </button>
          ) : (
            <div className="qr">
              {qr.isLoading && <Cargando texto="Generando QR…" />}
              {qr.isError && <ErrorBox error={qr.error} onRetry={() => qr.refetch()} />}
              {qr.data && (
                <>
                  <span className="qr-codigo">{qr.data.contenido}</span>
                  <button
                    type="button"
                    className="btn secundario"
                    onClick={() => setAbrir(false)}
                  >
                    Ocultar
                  </button>
                </>
              )}
            </div>
          )}
        </>
      )}
    </article>
  )
}

export function MisEntradasPage() {
  const { identity } = useIdentity()
  const [page, setPage] = useState(0)

  const entradas = useQuery({
    queryKey: ['mis-entradas', identity?.userId, page],
    queryFn: () => listarMisEntradas(page),
    enabled: identity?.role === 'CLIENTE',
  })

  if (identity?.role !== 'CLIENTE') {
    return (
      <section className="pagina">
        <h1>Mis entradas</h1>
        <Vacio>
          <strong>Necesitás el rol CLIENTE</strong>
          <p>Cambiá el usuario de prueba en la barra superior.</p>
        </Vacio>
      </section>
    )
  }

  return (
    <section className="pagina">
      <h1>Mis entradas</h1>

      {entradas.isLoading && <Cargando texto="Cargando entradas…" />}
      {entradas.isError && <ErrorBox error={entradas.error} onRetry={() => entradas.refetch()} />}

      {entradas.data && (
        <>
          {entradas.data.content.length === 0 ? (
            <Vacio>
              <strong>Todavía no tenés entradas</strong>
              <p>Comprá una desde el catálogo de eventos.</p>
            </Vacio>
          ) : (
            <div className="grid-entradas">
              {entradas.data.content.map((entrada) => (
                <EntradaCard key={entrada.id} entrada={entrada} />
              ))}
            </div>
          )}
          <Pagination page={entradas.data.page} onPage={setPage} />
        </>
      )}
    </section>
  )
}
