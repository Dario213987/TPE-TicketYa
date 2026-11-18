const formatoFecha = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const formatoFechaCorta = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'medium',
})

const formatoPrecio = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

export function formatFecha(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const fecha = new Date(iso)
  return Number.isNaN(fecha.getTime()) ? iso : formatoFecha.format(fecha)
}

export function formatFechaCorta(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const fecha = new Date(iso)
  return Number.isNaN(fecha.getTime()) ? iso : formatoFechaCorta.format(fecha)
}

export function formatPrecio(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return '—'
  }
  return formatoPrecio.format(valor)
}

export function precioDesde(precios: Record<string, number> | undefined): number | null {
  if (!precios) {
    return null
  }
  const valores = Object.values(precios)
  return valores.length > 0 ? Math.min(...valores) : null
}
