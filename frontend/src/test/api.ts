import { vi } from 'vitest'
import type { Me } from '../api/generated/model'

export interface ApiCall {
  method: string
  url: string
  body: unknown
}

export interface Reply {
  status: number
  body?: unknown
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}): Reply {
  return { status, body: { type: 'about:blank', title: 'Error', status, code, traceId: 'trace-1', ...extra } }
}

export const signedOut = problem(401, 'UNAUTHORIZED')

/** The active providers (D-30); stubApi answers with them unless a test handles the URL itself. */
export const loginProviders: Reply = {
  status: 200,
  body: [
    { id: 'google', name: 'Google' },
    { id: 'netid', name: 'netID' },
  ],
}

export const familyMember: Me = {
  id: '00000000-0000-4000-8000-000000000001',
  displayName: 'Mia Muster',
  email: 'mia@example.org',
  role: 'FAMILY_MEMBER',
  assignedToFamily: true,
}

/** Replaces fetch with a fake API; unknown requests get a 404 problem. Returns the recorded calls. */
export function stubApi(handle: (call: ApiCall) => Reply | undefined): ApiCall[] {
  const calls: ApiCall[] = []
  const fetchMock = vi.fn((url: string, init: RequestInit = {}) => {
    const call = {
      method: init.method ?? 'GET',
      url,
      body: typeof init.body === 'string' ? parse(init.body) : undefined,
    }
    calls.push(call)
    const reply =
      handle(call) ?? (call.url === '/api/v1/auth/providers' ? loginProviders : undefined) ?? problem(404, 'NOT_FOUND')
    const contentType = reply.status >= 400 ? 'application/problem+json' : 'application/json'
    return Promise.resolve(
      reply.body === undefined
        ? new Response(null, { status: reply.status === 200 ? 204 : reply.status })
        : new Response(JSON.stringify(reply.body), { status: reply.status, headers: { 'Content-Type': contentType } }),
    )
  })
  vi.stubGlobal('fetch', fetchMock)
  return calls
}

/** JSON bodies are compared as objects, other text (e.g. CSV) as it is. */
function parse(body: string): unknown {
  try {
    return JSON.parse(body) as unknown
  } catch {
    return body
  }
}
