import { useMemo, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { listarEventos } from '../api/eventos'
import type { EventoFiltros, TipoEvento } from '../api/types'
import { EventCard } from '../components/EventCard'
import { Icon } from '../components/Icon'
import { ErrorBox, Pagination } from '../components/ui'
import { ETIQUETA_TIPO, ICONO_TIPO, IMAGEN_TIPO, partesFecha } from '../lib/eventosVisuales'

const TIPOS: TipoEvento[] = [
  'RECITAL',
  'FESTIVAL',
  'TEATRO',
  'DEPORTE',
  'CONFERENCIA',
  'OTRO',
]

const CLAVES_FILTRO = [
  'participante',
  'tipo',
  'ciudad',
  'desde',
  'hasta',
  'orden',
] as const

export function CatalogoPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [avanzados, setAvanzados] = useState(() =>
    ['tipo', 'ciudad', 'desde', 'hasta', 'orden'].some((clave) => searchParams.get(clave)),
  )

  const filtros = useMemo<EventoFiltros>(
    () => ({
      participante: searchParams.get('participante') ?? '',
      tipo: (searchParams.get('tipo') as TipoEvento | null) ?? '',
      ciudad: searchParams.get('ciudad') ?? '',
      desde: searchParams.get('desde') ?? '',
      hasta: searchParams.get('hasta') ?? '',
      orden: (searchParams.get('orden') as EventoFiltros['orden']) ?? '',
      page: Number(searchParams.get('page') ?? 0),
      size: 12,
    }),
    [searchParams],
  )

  const eventos = useQuery({
    queryKey: ['eventos', filtros],
    queryFn: () => listarEventos(filtros),
  })

  const cantidadAvanzados = ['tipo', 'ciudad', 'desde', 'hasta', 'orden'].filter((clave) =>
    searchParams.get(clave),
  ).length
  const tipoActivo = (searchParams.get('tipo') as TipoEvento | null) ?? ''
  const ciudad = searchParams.get('ciudad') ?? ''
  const resultados = eventos.data?.content ?? []
  const recomendado = resultados[0]

  function aplicarFiltros(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const siguiente = new URLSearchParams()
    for (const clave of CLAVES_FILTRO) {
      const valor = String(data.get(clave) ?? '').trim()
      if (valor) {
        siguiente.set(clave, valor)
      }
    }
    setSearchParams(siguiente)
  }

  function limpiar() {
    setSearchParams(new URLSearchParams())
    setAvanzados(false)
  }

  function irAPagina(numero: number) {
    const siguiente = new URLSearchParams(searchParams)
    siguiente.set('page', String(numero))
    setSearchParams(siguiente)
  }

  function elegirTipo(tipo: TipoEvento | '') {
    const siguiente = new URLSearchParams(searchParams)
    if (tipo) {
      siguiente.set('tipo', tipo)
    } else {
      siguiente.delete('tipo')
    }
    siguiente.delete('page')
    setSearchParams(siguiente)
  }

  return (
    <div className="catalogo">
      <section className="hero">
        <div className="hero-image" />
        <div className="hero-shape hero-shape-one" />
        <div className="hero-shape hero-shape-two" />

        <form key={searchParams.toString()} className="hero-content" onSubmit={aplicarFiltros}>
          <span className="eyebrow">
            <span className="live-dot" />
            Tu próxima historia empieza acá
          </span>
          <h1>
            Encontrá eso que
            <br />
            <em>te hace vibrar.</em>
          </h1>
          <p>
            Descubrí recitales, festivales y experiencias cerca tuyo. Comprá en minutos y llevá tu
            entrada siempre con vos.
          </p>

          <div className="search-panel">
            <label className="search-field">
              <Icon name="search" size={19} />
              <input
                type="search"
                name="participante"
                placeholder="Buscá artistas, eventos o lugares"
                defaultValue={searchParams.get('participante') ?? ''}
                aria-label="Buscar artista o participante"
              />
            </label>
            <span className="search-divider" />
            <button
              type="button"
              className="location-field"
              onClick={() => setAvanzados((valor) => !valor)}
              title="Filtrar por ciudad"
            >
              <Icon name="location" size={19} />
              <span>
                {ciudad || 'Todas las ciudades'}
                <small>Ubicación</small>
              </span>
              <Icon name="arrow" size={16} />
            </button>
            <button type="submit" className="search-button">
              <Icon name="search" size={17} />
              Buscar
            </button>
          </div>

          <button
            type="button"
            className="advanced-trigger"
            onClick={() => setAvanzados((valor) => !valor)}
          >
            <Icon name="filter" size={15} />
            Búsqueda avanzada
            <span>{cantidadAvanzados || ''}</span>
          </button>

          {avanzados && (
            <div className="advanced-filters">
              <label>
                <span>Ciudad</span>
                <input
                  type="text"
                  name="ciudad"
                  placeholder="Ej.: Bahía Blanca"
                  defaultValue={ciudad}
                />
              </label>
              <label>
                <span>Tipo</span>
                <select name="tipo" defaultValue={tipoActivo}>
                  <option value="">Todos</option>
                  {TIPOS.map((tipo) => (
                    <option key={tipo} value={tipo}>
                      {ETIQUETA_TIPO[tipo]}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Desde</span>
                <input type="date" name="desde" defaultValue={searchParams.get('desde') ?? ''} />
              </label>
              <label>
                <span>Hasta</span>
                <input type="date" name="hasta" defaultValue={searchParams.get('hasta') ?? ''} />
              </label>
              <label>
                <span>Orden</span>
                <select name="orden" defaultValue={searchParams.get('orden') ?? ''}>
                  <option value="">Fecha</option>
                  <option value="precio">Menor precio</option>
                  <option value="relevancia">Relevancia</option>
                </select>
              </label>
              <button type="button" onClick={limpiar}>
                Limpiar
              </button>
            </div>
          )}

          <div className="hero-trust">
            <span>
              <Icon name="shield" size={13} />
              Compra 100% segura
            </span>
            <span>
              <Icon name="ticket" size={13} />
              Tu entrada siempre en el celu
            </span>
          </div>
        </form>
      </section>

      <section className="events-section">
        <div className="section-container">
          <div className="category-row" aria-label="Filtrar por tipo de evento">
            <button
              type="button"
              className={`category-pill${tipoActivo === '' ? ' active' : ''}`}
              onClick={() => elegirTipo('')}
            >
              <Icon name="sparkles" size={16} />
              Todos
            </button>
            {TIPOS.map((tipo) => (
              <button
                key={tipo}
                type="button"
                className={`category-pill${tipoActivo === tipo ? ' active' : ''}`}
                onClick={() => elegirTipo(tipo)}
              >
                <Icon name={ICONO_TIPO[tipo]} size={16} />
                {ETIQUETA_TIPO[tipo]}
              </button>
            ))}
          </div>

          <div className="section-heading">
            <div>
              <span className="section-kicker">Elegidos para vos</span>
              <h2>Eventos que no te podés perder</h2>
            </div>
            <button type="button" className="view-all" onClick={() => eventos.refetch()}>
              Actualizar <Icon name="arrow" size={15} />
            </button>
          </div>

          {eventos.isLoading && (
            <div className="events-grid" aria-busy="true">
              {[1, 2, 3, 4].map((numero) => (
                <div key={numero} className="event-card event-skeleton" />
              ))}
            </div>
          )}

          {eventos.isError && <ErrorBox error={eventos.error} onRetry={() => eventos.refetch()} />}

          {eventos.data && resultados.length === 0 && (
            <div className="empty-state">
              <Icon name="search" size={30} />
              <h3>No encontramos eventos</h3>
              <p>Probá con otros filtros o explorá todo el catálogo.</p>
              <button type="button" className="primary-button" onClick={limpiar}>
                Ver todos los eventos
              </button>
            </div>
          )}

          {resultados.length > 0 && (
            <div className="events-grid">
              {resultados.map((evento) => (
                <EventCard key={evento.id} evento={evento} />
              ))}
            </div>
          )}

          {eventos.data && <Pagination page={eventos.data.page} onPage={irAPagina} />}

          {recomendado && (
            <>
              <div className="recommendation-heading">
                <span className="personalized-badge">
                  <Icon name="sparkles" size={13} />
                  Para vos
                </span>
                <h2>También te puede interesar</h2>
              </div>
              <Link to={`/eventos/${recomendado.id}`} className="recommendation-strip">
                <img src={IMAGEN_TIPO[recomendado.tipo]} alt="" />
                <div>
                  <h3>{recomendado.nombre}</h3>
                  <p>
                    {ETIQUETA_TIPO[recomendado.tipo]} · {partesFecha(recomendado.fecha).dia}{' '}
                    {partesFecha(recomendado.fecha).mes}
                  </p>
                </div>
                <span className="view-all">
                  Ver evento <Icon name="arrow" size={15} />
                </span>
              </Link>
            </>
          )}
        </div>
      </section>

      <section className="operations-section section-container">
        <div className="section-heading">
          <div>
            <span className="section-kicker">Operación en tiempo real</span>
            <h2>Todo bajo control</h2>
          </div>
        </div>
        <div className="operations-grid">
          <Link to="/mis-entradas" className="operation-card">
            <span className="operation-icon">
              <Icon name="scan" size={22} />
            </span>
            <span>
              <strong>Control de acceso</strong>
              <small>Tus entradas y códigos QR, siempre a mano.</small>
            </span>
            <Icon name="arrow" size={16} />
          </Link>
          <Link to="/organizador" className="operation-card">
            <span className="operation-icon purple">
              <Icon name="chart" size={22} />
            </span>
            <span>
              <strong>Mis eventos y ventas</strong>
              <small>Publicá, gestioná sectores y consultá la venta.</small>
            </span>
            <Icon name="arrow" size={16} />
          </Link>
          <Link to="/preferencias" className="operation-card">
            <span className="operation-icon coral">
              <Icon name="bell" size={22} />
            </span>
            <span>
              <strong>Notificaciones</strong>
              <small>Recibí avisos de lo que más te interesa.</small>
            </span>
            <Icon name="arrow" size={16} />
          </Link>
        </div>
      </section>

      <section className="organizer-banner">
        <div>
          <span className="eyebrow">Para organizadores</span>
          <h2>
            <em>Creá. Publicá. Llená.</em>
            <br />
            ¿Tenés un evento en mente?
          </h2>
          <p>
            Cargá tu evento, definí sectores y precios, y empezá a vender entradas con control de
            acceso en tiempo real.
          </p>
          <Link to="/organizador/eventos/nuevo" className="search-button">
            Empezar ahora <Icon name="arrow" size={16} />
          </Link>
        </div>
        <div className="phone-card" aria-hidden="true">
          <div className="phone-notch" />
          <Icon name="ticket" size={34} />
          <strong>Tu evento, en movimiento.</strong>
          <div className="phone-chart">
            <i style={{ height: '35%' }} />
            <i style={{ height: '52%' }} />
            <i style={{ height: '44%' }} />
            <i style={{ height: '70%' }} />
            <i style={{ height: '62%' }} />
            <i style={{ height: '95%' }} />
          </div>
        </div>
        <div className="mini-stat" aria-hidden="true">
          +1.240 Entradas vendidas hoy
          <span>
            <i /> Venta activa
          </span>
        </div>
      </section>
    </div>
  )
}
