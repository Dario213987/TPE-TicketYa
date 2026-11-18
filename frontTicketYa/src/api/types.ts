export type TipoEvento =
  | 'RECITAL'
  | 'FESTIVAL'
  | 'TEATRO'
  | 'DEPORTE'
  | 'CONFERENCIA'
  | 'OTRO'

export type EstadoEvento =
  | 'BORRADOR'
  | 'CANCELADO'
  | 'FINALIZADO'
  | 'PUBLICADO'
  | 'SUSPENDIDO'
  | 'AGOTADO'

export type TipoSector = 'NUMERADO' | 'NO_NUMERADO'

export type Rol = 'CLIENTE' | 'ORGANIZADOR' | 'ADMINISTRADOR'

export interface PageMeta {
  size: number
  number: number
  totalElements: number
  totalPages: number
}

export interface Paged<T> {
  content: T[]
  page: PageMeta
}

// ---------- Eventos ----------

export interface EventoResumen {
  id: number
  nombre: string
  tipo: TipoEvento
  fecha: string
  estado: EstadoEvento
  altaDemanda?: boolean
  ciudad?: string
  participanteIds?: number[]
}

export interface Participante {
  id: number
  nombre: string
}

export interface Sector {
  id: number
  nombre: string
  precio?: number
  tipoSector: TipoSector
  capacidad?: number
  capacidadMaxima?: number
  filas?: number
  asientosPorFila?: number
}

export interface Lugar {
  id: number
  nombre: string
  tipo: string
  direccion: string
  ciudad: string
  latitud: number
  longitud: number
  capacidad?: number
  sectores?: Sector[]
}

export interface EventoDetalle {
  id: number
  nombre: string
  descripcion?: string
  tipo: TipoEvento
  fecha: string
  estado: EstadoEvento
  organizadorId: number
  altaDemanda?: boolean
  lugar?: Lugar
  participantes?: Participante[]
  sectores?: Sector[]
  preciosSectores?: Record<string, number>
}

export interface EventoEstado {
  id: number
  nombre: string
  estado: EstadoEvento
}

export interface EventoFiltros {
  tipo?: TipoEvento | ''
  organizadorId?: number | ''
  participante?: string
  participanteId?: number | ''
  ciudad?: string
  desde?: string
  hasta?: string
  fechaDesde?: string
  fechaHasta?: string
  orden?: 'fecha' | 'precio' | 'relevancia' | ''
  sort?: string
  page?: number
  size?: number
}

export interface EventoBorradorRequest {
  nombre: string
  descripcion: string
  tipo: TipoEvento
  fecha: string
  lugarId: number
  altaDemanda?: boolean
  participantes?: { id?: number; nombre?: string }[]
}

export interface EventoUpdateRequest {
  nombre?: string
  descripcion?: string
  tipo?: TipoEvento
  fecha?: string
  lugarId?: number
  altaDemanda?: boolean
  sectores?: number[]
}

export interface PrecioSectorResponse {
  eventoId: number
  sectorId: number
  precio: number
}

// ---------- Lugares / sectores ----------

export interface LugarRequest {
  nombre: string
  tipo: string
  direccion: string
  ciudad: string
  latitud: number
  longitud: number
  capacidad?: number
}

export interface SectorNumeradoRequest {
  nombre: string
  precio: number
  filas: number
  asientosPorFila: number
}

export interface SectorNoNumeradoRequest {
  nombre: string
  precio: number
  capacidadMaxima: number
}

// ---------- Tickets ----------

export type EstadoDisponibilidad = 'LIBRE' | 'RESERVADO' | 'VENDIDO'

export interface Asiento {
  fila: number
  numero: number
}

export interface AsientoDisponibilidad extends Asiento {
  estado: EstadoDisponibilidad
}

export interface SectorDisponibilidad {
  sectorId: number
  tipoSector: TipoSector
  capacidadTotal: number
  disponibles: number
  asientos?: AsientoDisponibilidad[]
}

export interface DisponibilidadEvento {
  eventoId: number
  limiteEntradasPorCliente: number
  altaDemanda?: boolean
  sectores: SectorDisponibilidad[]
}

export interface ReservaRequest {
  sectorId: number
  cantidad?: number
  asientos?: Asiento[]
  paseToken?: string
}

export interface ReservaResponse {
  idReserva: number
  eventoId: number
  clienteId: number
  sectorId: number
  cantidad: number
  limiteEntradasPorCliente: number
  entradasVigentes: number
  expiraEl: string
  codigosQr: string[]
  asientos?: AsientoDisponibilidad[]
}

export interface SectorDisponibilidadRequest {
  sectorId: number
  tipo: TipoSector
  capacidadMaxima?: number
  asientos?: Asiento[]
  filas?: number
  asientosPorFila?: number
  precio: number
}

export interface PublicarDisponibilidadRequest {
  eventoId: number
  limiteEntradasPorCliente?: number
  organizadorId?: number
  altaDemanda?: boolean
  sectores: SectorDisponibilidadRequest[]
}

export interface DisponibilidadResponse {
  eventoId: number
  yaExistia: boolean
  altaDemanda?: boolean
  sectoresProcesados: number
  asientosInsertados: number
  asientosOmitidos: number
  creadoEl: string
}

export interface ReporteSector {
  sectorId: number
  sectorNombre: string
  entradasVendidas: number
  entradasDevueltas: number
}

export interface ReporteEntradasVendidas {
  eventoId: number
  eventoNombre: string
  eventoFecha: string
  eventoEstado: string
  entradasVendidas: number
  entradasDevueltas: number
  sectores: ReporteSector[]
}

export interface ReporteOcupacionSector {
  sectorId: number
  sectorNombre: string
  ocupacion: number
}

export interface ReporteOcupacion {
  eventoId: number
  eventoNombre: string
  eventoFecha: string
  eventoEstado: string
  ocupacionTotal: number
  sectores: ReporteOcupacionSector[]
}

export interface ReporteRecaudacionSector {
  sectorId: number
  sectorNombre: string
  ventas: number
  reintegros: number
  recaudacion: number
}

export interface ReporteRecaudacion {
  eventoId: number
  eventoNombre: string
  eventoFecha: string
  eventoEstado: string
  ventas: number
  reintegros: number
  recaudacionTotal: number
  sectores: ReporteRecaudacionSector[]
}

export type CodigoRechazoAcceso = 'NO_EXISTE' | 'OTRO_EVENTO' | 'YA_USADA' | 'DEVUELTA'

export interface ValidarAccesoRequest {
  codigoQr: string
}

export interface ResultadoAcceso {
  resultado: string
  valido: boolean
  codigoQr: string
  eventoId: number
  estadoTicket: EstadoTicket
}

export type MedioPago = 'TARJETA_CREDITO' | 'TARJETA_DEBITO'

export type EstadoPago =
  | 'PENDIENTE_PAGO'
  | 'APROBADO'
  | 'RECHAZADO'
  | 'REINTEGRADO'
  | 'REINTEGRO_PENDIENTE'

export type EstadoTicket =
  | 'RESERVADO'
  | 'PENDIENTE_PAGO'
  | 'EMITIDO'
  | 'VENDIDO'
  | 'USADO'
  | 'DEVUELTO'
  | 'CANCELADO'
  | 'EXPIRADO'
  | 'LIBERADO'

export interface TicketEmitido {
  id: number
  codigoQr: string
  estado: EstadoTicket
  sectorId: number
  fila?: number | null
  numero?: number | null
  precio: number
}

export interface CompraResponse {
  reservaId: number
  ventaId?: number | null
  eventoId: number
  clienteId: number
  medioPago: MedioPago
  estadoPago: EstadoPago
  total: number
  pagadoEl?: string | null
  tickets: TicketEmitido[]
}

export interface MiEntrada {
  id: number
  eventoId: number
  sectorId: number
  fila?: number | null
  numero?: number | null
  estado: EstadoTicket
  utilizado: boolean
  precio?: number
  fechaEmision?: string
}

export interface QrEntrada {
  ticketId: number
  contenido: string
}

export type EstadoFila = 'EN_ESPERA' | 'HABILITADO'

export interface Pase {
  token: string
  tanda: string
  expiraEl: string
}

export interface TurnoFila {
  eventoId: number
  clienteId: number
  posicion?: number | null
  totalEnFila: number
  yaEstabaEnFila: boolean
  ingresadoEl: string
  estado: EstadoFila
  pase?: Pase | null
}

export interface PosicionFila {
  eventoId: number
  clienteId: number
  posicion?: number | null
  personasDelante?: number | null
  totalEnFila: number
  estado: EstadoFila
  pase?: Pase | null
}

// ---------- Preferencias ----------

export interface Preferencias {
  clienteId: number
  tiposFavoritos: TipoEvento[]
  artistasFavoritos: number[]
  ciudad?: string | null
}

export interface PreferenciasRequest {
  tiposFavoritos: TipoEvento[]
  artistasFavoritos: number[]
  ciudad: string
}

export interface HistorialCompra {
  eventoId: number
  tipo?: TipoEvento
  participanteIds?: number[]
  ciudad?: string | null
  fecha?: string | null
  comprado: boolean
  usado: boolean
}

export type MotivoRecomendacion = 'ARTISTA' | 'TIPO' | 'CO_COMPRA' | 'CIUDAD' | 'PROXIMO'

export interface Recomendacion {
  eventoId: number
  nombre: string
  tipo: TipoEvento
  fecha: string
  estado: string
  ciudad?: string | null
  motivo: MotivoRecomendacion
  puntaje: number
}

// ---------- Devoluciones ----------

export type EstadoDevolucion = 'PENDIENTE' | 'DENEGADA' | 'APROBADA'

export interface SolicitudDevolucionRequest {
  ticketId: number
  clienteId?: number
  motivo?: string
}

export interface Devolucion {
  id: number
  clienteId: number
  ticketId: number
  eventoId?: number | null
  fecha: string
  estado: EstadoDevolucion
  transaccionId?: number | null
  motivo?: string | null
}

export interface DevolucionFiltros {
  estado?: EstadoDevolucion | ''
  clienteId?: number | ''
  eventoId?: number | ''
  page?: number
  size?: number
  sort?: string
}
