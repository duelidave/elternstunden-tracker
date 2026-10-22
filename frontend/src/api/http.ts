import type { Problem, ValidationError } from './generated/model'

/**
 * Fetch wrapper for the generated API client (orval mutator, D-25).
 * - same-origin cookies (server-side session, D-20)
 * - CSRF header from the XSRF-TOKEN cookie on state-changing requests (Lastenheft 8.1)
 * - application/problem+json errors become an ApiProblem (RFC 9457)
 */

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export class ApiProblem extends Error {
  readonly status: number
  readonly code: string
  readonly traceId: string | undefined
  readonly errors: readonly ValidationError[]
  /** The whole problem body, including extension fields such as maskedEmail. */
  readonly properties: Readonly<Record<string, unknown>>

  constructor(status: number, problem: (Partial<Problem> & Record<string, unknown>) | undefined) {
    super(problem?.detail ?? `HTTP ${String(status)}`)
    this.name = 'ApiProblem'
    this.status = status
    this.code = problem?.code ?? 'UNKNOWN'
    this.traceId = problem?.traceId
    this.errors = problem?.errors ?? []
    this.properties = problem ?? {}
  }
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith(prefix))
  return cookie === undefined ? undefined : decodeURIComponent(cookie.slice(prefix.length))
}

function parseBody(text: string): unknown {
  if (text === '') {
    return undefined
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return undefined
  }
}

export async function apiFetch<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (UNSAFE_METHODS.has(method)) {
    const token = readCookie(CSRF_COOKIE)
    if (token !== undefined) {
      headers.set(CSRF_HEADER, token)
    }
  }

  // orval JSON-encodes every request body; a text body such as the CSV import is sent as the plain text
  const body =
    headers.get('Content-Type') === 'text/csv' && typeof init.body === 'string'
      ? (JSON.parse(init.body) as string)
      : init.body

  const response = await fetch(url, { ...init, method, headers, body, credentials: 'same-origin' })
  const result = parseBody(response.status === 204 ? '' : await response.text())

  if (!response.ok) {
    throw new ApiProblem(response.status, result as (Partial<Problem> & Record<string, unknown>) | undefined)
  }
  return result as T
}
