import type { ReactNode } from 'react'
import { mensajeDeError } from '../api/http'
import type { EstadoEvento, EstadoTicket, PageMeta } from '../api/types'

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="estado-carga" role="status">
      <span className="spinner" aria-hidden="true" />
      {texto}
    </div>
  )
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="mensaje error" role="alert">
      <p>{mensajeDeError(error)}</p>
      {onRetry && (
        <button type="button" className="btn secundario" onClick={onRetry}>
          Reintentar
        </button>
      )}
    </div>
  )
}

export function Vacio({ children }: { children: ReactNode }) {
  return <div className="mensaje vacio">{children}</div>
}

const ETIQUETAS_ESTADO: Record<EstadoEvento, string> = {
  BORRADOR: 'Borrador',
  CANCELADO: 'Cancelado',
  FINALIZADO: 'Finalizado',
  PUBLICADO: 'Publicado',
  SUSPENDIDO: 'Suspendido',
  AGOTADO: 'Agotado',
}

export function EstadoBadge({ estado }: { estado: EstadoEvento }) {
  return <span className={`badge estado-${estado.toLowerCase()}`}>{ETIQUETAS_ESTADO[estado]}</span>
}

const ETIQUETAS_TICKET: Record<EstadoTicket, string> = {
  RESERVADO: 'Reservado',
  PENDIENTE_PAGO: 'Pendiente de pago',
  EMITIDO: 'Emitido',
  VENDIDO: 'Vendido',
  USADO: 'Usado',
  DEVUELTO: 'Devuelto',
  CANCELADO: 'Cancelado',
  EXPIRADO: 'Expirado',
  LIBERADO: 'Liberado',
}

export function TicketBadge({ estado }: { estado: EstadoTicket }) {
  return <span className={`badge ticket-${estado.toLowerCase()}`}>{ETIQUETAS_TICKET[estado]}</span>
}

export function Pagination({
  page,
  onPage,
}: {
  page: PageMeta
  onPage: (numero: number) => void
}) {
  if (page.totalPages <= 1) {
    return null
  }
  return (
    <nav className="paginacion" aria-label="Paginación">
      <button
        type="button"
        className="btn secundario"
        disabled={page.number <= 0}
        onClick={() => onPage(page.number - 1)}
      >
        Anterior
      </button>
      <span>
        Página {page.number + 1} de {page.totalPages} · {page.totalElements} resultados
      </span>
      <button
        type="button"
        className="btn secundario"
        disabled={page.number >= page.totalPages - 1}
        onClick={() => onPage(page.number + 1)}
      >
        Siguiente
      </button>
    </nav>
  )
}
