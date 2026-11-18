import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import type { Rol } from '../api/types'
import { IDENTIDADES, ROLES, type Identity } from '../auth/storage'
import { useIdentity } from '../auth/useIdentity'
import { Icon, Logo } from './Icon'

type Panel = 'sesion' | 'notificaciones' | null

function SessionModal({ onClose }: { onClose: () => void }) {
  const { identity, setIdentity } = useIdentity()
  const userId = identity?.userId ?? 100
  const role: Rol = identity?.role ?? 'CLIENTE'
  const presetIndex = IDENTIDADES.findIndex(
    (item) => item.userId === userId && item.role === role,
  )

  function aplicarPreset(index: number) {
    if (index >= 0) {
      setIdentity(IDENTIDADES[index])
    }
  }

  function cambiarUsuario(valor: string) {
    const numero = Number(valor)
    if (!Number.isFinite(numero) || numero < 1) {
      return
    }
    const preset = IDENTIDADES.find((item) => item.userId === numero && item.role === role)
    setIdentity({ userId: numero, role, nombre: preset?.nombre ?? 'Personalizado' })
  }

  function cambiarRol(valor: string) {
    const nuevoRol = valor as Rol
    const preset = IDENTIDADES.find((item) => item.userId === userId && item.role === nuevoRol)
    setIdentity({ userId, role: nuevoRol, nombre: preset?.nombre ?? 'Personalizado' })
  }

  return (
    <section className="session-modal" role="dialog" aria-modal="true" aria-label="Tu sesión">
      <div className="panel-heading">
        <div>
          <span className="section-kicker">Identidad de prueba</span>
          <h2>{identity ? 'Tu sesión' : 'Ingresá a TicketYa'}</h2>
        </div>
        <button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
          <Icon name="close" size={18} />
        </button>
      </div>

      <p>
        Elegí con qué usuario querés navegar. La identidad se envía al backend en las headers{' '}
        <code className="inline-code">X-User-Id</code> y{' '}
        <code className="inline-code">X-User-Role</code>.
      </p>

      {identity && (
        <div className="session-card">
          <Icon name="user" size={22} />
          <div>
            <strong>{identity.nombre}</strong>
            <small>
              ID {identity.userId} · {identity.role}
            </small>
          </div>
          <Icon name="check" size={18} />
        </div>
      )}

      <div className="notice">
        Los permisos dependen del rol: los clientes compran, los organizadores publican eventos y
        los administradores gestionan el catálogo.
      </div>

      <label className="field">
        <span>Usuario de prueba</span>
        <select
          value={presetIndex >= 0 ? String(presetIndex) : '-1'}
          onChange={(event) => aplicarPreset(Number(event.target.value))}
          aria-label="Usuario de prueba"
        >
          {IDENTIDADES.map((item: Identity, index) => (
            <option key={`${item.userId}-${item.role}`} value={index}>
              {item.nombre} · {item.role}
            </option>
          ))}
          {presetIndex < 0 && <option value="-1">Personalizado</option>}
        </select>
      </label>

      <div className="field-row">
        <label className="field">
          <span>X-User-Id</span>
          <input
            type="number"
            min={1}
            value={userId}
            onChange={(event) => cambiarUsuario(event.target.value)}
          />
        </label>
        <label className="field">
          <span>Rol</span>
          <select value={role} onChange={(event) => cambiarRol(event.target.value)}>
            {ROLES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="form-actions">
        <button type="button" className="primary-button" onClick={onClose}>
          Listo
        </button>
      </div>
    </section>
  )
}

function NotificationsPanel({ onClose }: { onClose: () => void }) {
  return (
    <section
      className="notifications-panel"
      role="dialog"
      aria-modal="true"
      aria-label="Notificaciones"
    >
      <div className="panel-heading">
        <div>
          <span className="section-kicker">Centro de novedades</span>
          <h2>Notificaciones</h2>
        </div>
        <button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
          <Icon name="close" size={18} />
        </button>
      </div>
      <p>Ejemplos de avisos que podrías recibir sobre tus eventos y compras.</p>
      <div className="notifications-list">
        <div className="notification-item">
          <span className="notification-icon success">
            <Icon name="check" size={18} />
          </span>
          <div>
            <strong>Compra confirmada</strong>
            <small>Tu entrada ya está disponible en Mis entradas.</small>
          </div>
        </div>
        <div className="notification-item">
          <span className="notification-icon">
            <Icon name="music" size={18} />
          </span>
          <div>
            <strong>¡Tu artista favorito vuelve!</strong>
            <small>Hay nuevas fechas disponibles en el catálogo.</small>
          </div>
        </div>
        <div className="notification-item">
          <span className="notification-icon warning">
            <Icon name="calendar" size={18} />
          </span>
          <div>
            <strong>Cambio de horario</strong>
            <small>Revisá el detalle del evento antes de asistir.</small>
          </div>
        </div>
      </div>
      <div className="form-actions">
        <button type="button" className="primary-button" onClick={onClose}>
          Entendido
        </button>
      </div>
    </section>
  )
}

export function Layout() {
  const { identity } = useIdentity()
  const [panel, setPanel] = useState<Panel>(null)
  const rol = identity?.role
  const destinoPublicar = rol === 'ORGANIZADOR' ? '/organizador/eventos/nuevo' : '/organizador'

  return (
    <div className="app-shell">
      <header className="header">
        <div className="nav-container">
          <NavLink to="/" aria-label="TicketYa, ir al catálogo">
            <Logo />
          </NavLink>

          <nav className="desktop-nav" aria-label="Navegación principal">
            <NavLink to="/" end className="nav-link">
              Explorar eventos
            </NavLink>
            {rol === 'CLIENTE' && (
              <NavLink to="/mis-entradas" className="nav-link">
                Mis entradas
              </NavLink>
            )}
            {rol === 'CLIENTE' && (
              <NavLink to="/preferencias" className="nav-link">
                Preferencias
              </NavLink>
            )}
            {rol === 'ORGANIZADOR' && (
              <NavLink to="/organizador" className="nav-link">
                Organizador
              </NavLink>
            )}
            {rol === 'ADMINISTRADOR' && (
              <NavLink to="/admin" className="nav-link">
                Administración
              </NavLink>
            )}
          </nav>

          <div className="nav-actions">
            <button
              type="button"
              className="notification-button"
              onClick={() => setPanel('notificaciones')}
              aria-label="Ver notificaciones"
            >
              <Icon name="bell" size={19} />
              <span />
            </button>
            <NavLink to={destinoPublicar} className="publish-button">
              Publicá tu evento
            </NavLink>
            <button
              type="button"
              className="account-button"
              onClick={() => setPanel('sesion')}
              aria-label="Cambiar identidad de usuario"
            >
              <Icon name="user" size={17} />
              <span className="account-name">{identity?.nombre ?? 'Ingresar'}</span>
            </button>
          </div>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="site-footer">
        TicketYa · Arquitectura Web 2026 · Rauch
      </footer>

      {panel && (
        <div className="modal-backdrop" onClick={() => setPanel(null)}>
          <div onClick={(event) => event.stopPropagation()}>
            {panel === 'sesion' ? (
              <SessionModal onClose={() => setPanel(null)} />
            ) : (
              <NotificationsPanel onClose={() => setPanel(null)} />
            )}
          </div>
        </div>
      )}
    </div>
  )
}
