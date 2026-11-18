import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { EventoResumen } from '../api/types'
import { Icon } from './Icon'
import {
  ETIQUETA_ESTADO,
  ETIQUETA_TIPO,
  IMAGEN_TIPO,
  esEstadoDestacado,
  partesFecha,
} from '../lib/eventosVisuales'

export function EventCard({ evento }: { evento: EventoResumen }) {
  const [esFavorito, setEsFavorito] = useState(false)
  const { dia, mes } = partesFecha(evento.fecha)
  const etiqueta = evento.altaDemanda
    ? 'Alta demanda'
    : esEstadoDestacado(evento.estado)
      ? ETIQUETA_ESTADO[evento.estado]
      : null

  return (
    <article className="event-card">
      <div className="event-image-wrap">
        <img className="event-image" src={IMAGEN_TIPO[evento.tipo]} alt="" />
        {etiqueta && <span className="event-tag">{etiqueta}</span>}
        <button
          type="button"
          className={`favorite${esFavorito ? ' is-favorite' : ''}`}
          onClick={() => setEsFavorito((valor) => !valor)}
          aria-label={esFavorito ? 'Quitar de favoritos' : 'Agregar a favoritos'}
          aria-pressed={esFavorito}
        >
          <Icon name="heart" size={16} filled={esFavorito} />
        </button>
        <div className="date-tile">
          <strong>{dia}</strong>
          <span>{mes}</span>
        </div>
      </div>
      <Link to={`/eventos/${evento.id}`} className="card-content">
        <span className="event-category">{ETIQUETA_TIPO[evento.tipo]}</span>
        <h3 className="event-title">{evento.nombre}</h3>
        <span className="event-place">
          <Icon name="location" size={13} />
          Evento #{evento.id}
        </span>
        <div className="event-footer">
          <span>
            Entradas
            <small>Ver precios y disponibilidad</small>
          </span>
          <span className="card-arrow">
            <Icon name="arrow" size={15} />
          </span>
        </div>
      </Link>
    </article>
  )
}
