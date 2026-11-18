import { readIdentity } from '../auth/storage'

const BASE_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/+$/, '') ?? ''

export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  correlationId?: string
  errors?: { field?: string; message?: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly detail?: string
  readonly correlationId?: string
  readonly errors?: { field?: string; message?: string }[]

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? 'La solicitud no pudo completarse')
    this.name = 'ApiError'
    this.status = status
    this.detail = problem.detail
    this.correlationId = problem.correlationId
    this.errors = problem.errors
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const identity = readIdentity()
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.headers,
  }

  if (identity) {
    headers['X-User-Id'] = String(identity.userId)
    headers['X-User-Role'] = identity.role
  }

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  })

  const contentType = response.headers.get('content-type') ?? ''
  const esJson = contentType.includes('json')
  const payload: unknown =
    response.status === 204 ? null : esJson ? await response.json() : await response.text()

  if (!response.ok) {
    const problem: ProblemDetail =
      esJson && payload && typeof payload === 'object' ? (payload as ProblemDetail) : {}
    throw new ApiError(response.status, problem)
  }

  return payload as T
}

export function buildQuery(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError) {
    const detalleErrores = error.errors
      ?.map((item) => item.message)
      .filter(Boolean)
      .join('. ')
    return [error.message, detalleErrores].filter(Boolean).join('. ')
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Ocurrió un error inesperado'
}

export function esNoEncontrado(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
