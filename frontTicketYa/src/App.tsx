import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { IdentityProvider } from './auth/IdentityContext'
import { Layout } from './components/Layout'
import { AdminPage } from './pages/admin/AdminPage'
import { CatalogoPage } from './pages/CatalogoPage'
import { EventoDetallePage } from './pages/EventoDetallePage'
import { MisEntradasPage } from './pages/MisEntradasPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PreferenciasPage } from './pages/PreferenciasPage'
import { OrganizadorEventoNuevoPage } from './pages/organizador/OrganizadorEventoNuevoPage'
import { OrganizadorEventoPage } from './pages/organizador/OrganizadorEventoPage'
import { OrganizadorLugarPage } from './pages/organizador/OrganizadorLugarPage'
import { OrganizadorLugaresPage } from './pages/organizador/OrganizadorLugaresPage'
import { OrganizadorPage } from './pages/organizador/OrganizadorPage'

export default function App() {
  return (
    <BrowserRouter>
      <IdentityProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<CatalogoPage />} />
            <Route path="eventos/:eventoId" element={<EventoDetallePage />} />
            <Route path="mis-entradas" element={<MisEntradasPage />} />
            <Route path="preferencias" element={<PreferenciasPage />} />
            <Route path="organizador" element={<OrganizadorPage />} />
            <Route path="organizador/eventos/nuevo" element={<OrganizadorEventoNuevoPage />} />
            <Route path="organizador/eventos/:eventoId" element={<OrganizadorEventoPage />} />
            <Route path="organizador/lugares" element={<OrganizadorLugaresPage />} />
            <Route path="organizador/lugares/:lugarId" element={<OrganizadorLugarPage />} />
            <Route path="admin" element={<AdminPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </IdentityProvider>
    </BrowserRouter>
  )
}
