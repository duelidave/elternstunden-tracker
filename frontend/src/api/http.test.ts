import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiProblem, apiFetch } from './http'

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function sentHeaders(fetchMock: ReturnType<typeof vi.fn>): Headers {
  const init = fetchMock.mock.calls[0]?.[1] as RequestInit
  return new Headers(init.headers)
}

describe('apiFetch', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('sends the CSRF token from the cookie on state-changing requests', async () => {
    document.cookie = 'XSRF-TOKEN=token%20value'
    const fetchMock = stubFetch(new Response(null, { status: 204 }))

    await apiFetch('/api/v1/something', { method: 'POST' })

    expect(sentHeaders(fetchMock).get('X-XSRF-TOKEN')).toBe('token value')
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ credentials: 'same-origin' })
  })

  it('does not send the CSRF token on GET', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    const fetchMock = stubFetch(new Response('{"status":"UP"}', { status: 200 }))

    const body = await apiFetch<{ status: string }>('/api/v1/status')

    expect(body).toEqual({ status: 'UP' })
    expect(sentHeaders(fetchMock).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('turns problem responses into an ApiProblem', async () => {
    stubFetch(
      new Response(
        JSON.stringify({
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'Request validation failed.',
          code: 'VALIDATION_FAILED',
          traceId: 'trace-1',
          errors: [{ field: 'description', code: 'Size', message: 'size must be between 3 and 500' }],
        }),
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const error = await apiFetch('/api/v1/something', { method: 'POST' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      traceId: 'trace-1',
      errors: [{ field: 'description', code: 'Size' }],
    })
    expect((error as ApiProblem).properties).toMatchObject({ title: 'Bad Request' })
  })

  it('still fails cleanly when the error body is not JSON', async () => {
    stubFetch(new Response('Bad Gateway', { status: 502 }))

    const error = await apiFetch('/api/v1/status').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({ status: 502, code: 'UNKNOWN' })
  })
})
