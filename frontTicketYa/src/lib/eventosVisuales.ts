import type { EstadoEvento, TipoEvento } from '../api/types'

export const ETIQUETA_TIPO: Record<TipoEvento, string> = {
  RECITAL: 'Recital',
  FESTIVAL: 'Festival',
  TEATRO: 'Teatro',
  DEPORTE: 'Deporte',
  CONFERENCIA: 'Conferencia',
  OTRO: 'Otros',
}

export const ICONO_TIPO: Record<TipoEvento, string> = {
  RECITAL: 'music',
  FESTIVAL: 'confetti',
  TEATRO: 'masks',
  DEPORTE: 'ball',
  CONFERENCIA: 'mic',
  OTRO: 'sparkles',
}

export const IMAGEN_TIPO: Record<TipoEvento, string> = {
  RECITAL:
    'https://images.unsplash.com/photo-1618613403887-ed08ea9f8f6e?auto=format&fit=crop&w=1200&q=85',
  FESTIVAL:
    'https://images.unsplash.com/photo-1672950990148-7d9da75d0148?auto=format&fit=crop&w=1200&q=85',
  DEPORTE:
    'https://images.unsplash.com/photo-1705593973313-75de7bf95b56?auto=format&fit=crop&w=1200&q=85',
  TEATRO:
    'https://images.unsplash.com/photo-1588638874655-9ff1b9c6668b?auto=format&fit=crop&w=1200&q=85',
  CONFERENCIA:
    'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1200&q=85',
  OTRO:
    'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=85',
}

const MESES = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC']

export function partesFecha(fecha: string) {
  const coincidencia = /^(\d{4})-(\d{2})-(\d{2})/.exec(fecha)
  if (coincidencia) {
    const mes = Number(coincidencia[2]) - 1
    if (mes >= 0 && mes < MESES.length) {
      return { dia: coincidencia[3], mes: MESES[mes] }
    }
  }
  const fechaEvento = new Date(fecha)
  if (Number.isNaN(fechaEvento.getTime())) {
    return { dia: '--', mes: '---' }
  }
  return {
    dia: String(fechaEvento.getDate()).padStart(2, '0'),
    mes: MESES[fechaEvento.getMonth()],
  }
}

export const ETIQUETA_ESTADO: Record<EstadoEvento, string> = {
  BORRADOR: 'Borrador',
  CANCELADO: 'Cancelado',
  FINALIZADO: 'Finalizado',
  PUBLICADO: 'Publicado',
  SUSPENDIDO: 'Suspendido',
  AGOTADO: 'Agotado',
}

export function etiquetaEstado(estado: EstadoEvento) {
  return ETIQUETA_ESTADO[estado]
}

export function esEstadoDestacado(estado: EstadoEvento) {
  return estado !== 'PUBLICADO' && estado !== 'BORRADOR'
}
