import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { texts } from './i18n/de'

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

describe('App', () => {
  it('shows that the server is reachable via the generated status hook', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { status: 'UP' }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp()

    expect(await screen.findByText(texts.status.reachable)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/status', expect.objectContaining({ method: 'GET' }))
  })

  it('shows that the server is not reachable on an error', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse(500, { status: 500, code: 'INTERNAL_ERROR' }, 'application/problem+json'),
        ),
    )

    renderApp()

    expect(await screen.findByText(texts.status.unreachable)).toBeInTheDocument()
  })
})
