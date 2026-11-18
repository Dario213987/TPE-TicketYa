import { useMemo, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { pagarReserva, reservar } from '../api/tickets'
import type {
  CompraResponse,
  DisponibilidadEvento,
  MedioPago,
  ReservaResponse,
} from '../api/types'
import { formatFecha, formatPrecio } from '../lib/format'
import { ErrorBox } from './ui'

function nuevaClaveIdempotencia(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function ReservationPanel({
  eventoId,
  disponibilidad,
  paseRequerido,
  paseToken,
}: {
  eventoId: number
  disponibilidad: DisponibilidadEvento
  paseRequerido: boolean
  paseToken: string | null
}) {
  const queryClient = useQueryClient()
  const clavePago = useRef<string>('')

  const sectoresDisponibles = useMemo(
    () => disponibilidad.sectores.filter((sector) => sector.disponibles > 0),
    [disponibilidad],
  )

  const [sectorId, setSectorId] = useState<number>(
    sectoresDisponibles[0]?.sectorId ?? 0,
  )
  const [cantidad, setCantidad] = useState(1)
  const [medioPago, setMedioPago] = useState<MedioPago>('TARJETA_CREDITO')
  const [reserva, setReserva] = useState<ReservaResponse | null>(null)
  const [compra, setCompra] = useState<CompraResponse | null>(null)

  const sector = disponibilidad.sectores.find((item) => item.sectorId === sectorId)
  const maximo = Math.min(
    sector?.disponibles ?? 0,
    disponibilidad.limiteEntradasPorCliente,
  )

  const reservarMutation = useMutation({
    mutationFn: () =>
      reservar(eventoId, {
        sectorId,
        cantidad,
        paseToken: paseToken ?? undefined,
      }),
    onSuccess: (data) => {
      clavePago.current = nuevaClaveIdempotencia()
      setReserva(data)
      setCompra(null)
      void queryClient.invalidateQueries({ queryKey: ['disponibilidad', eventoId] })
    },
  })

  const pagarMutation = useMutation({
    mutationFn: () => {
      if (!reserva) {
        throw new Error('No hay una reserva para pagar')
      }
      return pagarReserva(reserva.idReserva, medioPago, clavePago.current)
    },
    onSuccess: (data) => {
      setCompra(data)
      void queryClient.invalidateQueries({ queryKey: ['disponibilidad', eventoId] })
      void queryClient.invalidateQueries({ queryKey: ['mis-entradas'] })
    },
  })

  function reiniciar() {
    setReserva(null)
    setCompra(null)
    clavePago.current = ''
  }

  if (sectoresDisponibles.length === 0 && !reserva) {
    return (
      <section className="panel panel-compra">
        <h2>Comprar entradas</h2>
        <p className="nota">No hay entradas disponibles para este evento.</p>
      </section>
    )
  }

  if (compra) {
    const aprobado = compra.estadoPago === 'APROBADO'
    return (
      <section className="panel panel-compra">
        <h2>{aprobado ? 'Compra confirmada' : 'Pago pendiente'}</h2>
        <p>
          Estado: <strong>{compra.estadoPago}</strong> · Total {formatPrecio(compra.total)}
        </p>
        {aprobado ? (
          <>
            <ul className="lista-tickets">
              {compra.tickets.map((ticket) => (
                <li key={ticket.id}>
                  <span>
                    Sector {ticket.sectorId}
                    {ticket.fila ? ` · Fila ${ticket.fila}` : ''}
                    {ticket.numero ? ` · Asiento ${ticket.numero}` : ''}
                  </span>
                  <code>{ticket.codigoQr}</code>
                </li>
              ))}
            </ul>
            <Link className="btn" to="/mis-entradas">
              Ir a mis entradas
            </Link>
          </>
        ) : (
          <>
            <p className="nota">
              El pago quedó pendiente. Podés reintentarlo sin riesgo de cobrar dos veces.
            </p>
            {pagarMutation.isError && <ErrorBox error={pagarMutation.error} />}
            <button
              type="button"
              className="btn"
              onClick={() => pagarMutation.mutate()}
              disabled={pagarMutation.isPending}
            >
              {pagarMutation.isPending ? 'Reintentando…' : 'Reintentar pago'}
            </button>
          </>
        )}
      </section>
    )
  }

  if (reserva) {
    return (
      <section className="panel panel-compra">
        <h2>Reserva #{reserva.idReserva}</h2>
        <p>
          {reserva.cantidad} entrada(s) reservada(s). Tenés tiempo hasta{' '}
          <strong>{formatFecha(reserva.expiraEl)}</strong>.
        </p>
        <label className="campo">
          <span>Medio de pago</span>
          <select
            value={medioPago}
            onChange={(event) => setMedioPago(event.target.value as MedioPago)}
          >
            <option value="TARJETA_CREDITO">Tarjeta de crédito</option>
            <option value="TARJETA_DEBITO">Tarjeta de débito</option>
          </select>
        </label>
        {pagarMutation.isError && <ErrorBox error={pagarMutation.error} />}
        <div className="acciones">
          <button
            type="button"
            className="btn"
            onClick={() => pagarMutation.mutate()}
            disabled={pagarMutation.isPending}
          >
            {pagarMutation.isPending ? 'Pagando…' : 'Pagar reserva'}
          </button>
          <button type="button" className="btn secundario" onClick={reiniciar}>
            Descartar
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="panel panel-compra">
      <h2>Comprar entradas</h2>

      {paseRequerido && !paseToken && (
        <p className="mensaje aviso">Entrá a la fila virtual para obtener un pase de compra.</p>
      )}

      <label className="campo">
        <span>Sector</span>
        <select
          value={sectorId}
          onChange={(event) => {
            setSectorId(Number(event.target.value))
            setCantidad(1)
          }}
        >
          {disponibilidad.sectores.map((item) => (
            <option key={item.sectorId} value={item.sectorId} disabled={item.disponibles === 0}>
              Sector {item.sectorId} · {item.disponibles} disponibles
            </option>
          ))}
        </select>
      </label>

      <label className="campo">
        <span>Cantidad (máx. {maximo})</span>
        <input
          type="number"
          min={1}
          max={maximo}
          value={cantidad}
          onChange={(event) =>
            setCantidad(Math.max(1, Math.min(maximo, Number(event.target.value))))
          }
        />
      </label>

      {reservarMutation.isError && <ErrorBox error={reservarMutation.error} />}

      <button
        type="button"
        className="btn"
        onClick={() => reservarMutation.mutate()}
        disabled={
          reservarMutation.isPending || !sectorId || maximo < 1 || (paseRequerido && !paseToken)
        }
      >
        {reservarMutation.isPending ? 'Reservando…' : 'Reservar'}
      </button>
    </section>
  )
}
