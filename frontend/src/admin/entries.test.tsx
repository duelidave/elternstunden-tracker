import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AdminEntry } from '../api/generated/model'
import { texts } from '../i18n/de'
import { type ApiCall, familyMember, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const admin = { ...familyMember, role: 'ADMIN' as const, assignedToFamily: false }

function entry(id: string, familyId: string, familyLabel: string, changes: Partial<AdminEntry> = {}): AdminEntry {
  return {
    id,
    familyId,
    familyLabel,
    schoolYearId: 'year-1',
    schoolYearClosed: false,
    workDate: '2026-10-14',
    durationMinutes: 60,
    description: `Eintrag ${id}`,
    status: 'OPEN',
    createdByDisplayName: 'Mia Muster',
    createdAt: '2026-10-14T10:00:00Z',
    version: 0,
    ...changes,
  }
}

const entries = [
  entry('e1', 'family-1', 'Muster'),
  entry('e2', 'family-2', 'Müller'),
  entry('e3', 'family-1', 'Muster', { durationMinutes: 120 }),
]

const page = (content: unknown[]) => ({ content, page: 0, size: 200, totalElements: content.length, totalPages: 1 })

function api(handle: (call: ApiCall) => { status: number; body?: unknown } | undefined = () => undefined) {
  return stubApi((call) => {
    if (call.url === '/api/v1/me') {
      return { status: 200, body: admin }
    }
    if (call.method === 'GET' && call.url.startsWith('/api/v1/admin/entries')) {
      return { status: 200, body: page(entries) }
    }
    if (call.url.startsWith('/api/v1/admin/families')) {
      return { status: 200, body: page([]) }
    }
    return handle(call)
  })
}

async function main() {
  return within(await screen.findByRole('main', {}, { timeout: 3000 }))
}

describe('confirming entries (FS-CONF)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('groups open entries by family and shows the count in the navigation (7.5)', async () => {
    const calls = api()

    renderRoute('/admin/entries')
    const view = await main()

    expect(await view.findByRole('heading', { name: 'Muster' })).toBeInTheDocument()
    expect(view.getByRole('heading', { name: 'Müller' })).toBeInTheDocument()
    expect(view.getByRole('link', { name: new RegExp(texts.admin.nav.openCount(3)) })).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'GET', url: '/api/v1/admin/entries?status=OPEN&page=0&size=200', body: undefined })
  })

  it('confirms a selection at once', async () => {
    const calls = api((call) =>
      call.method === 'POST' && call.url === '/api/v1/admin/entries/confirm'
        ? { status: 200, body: { confirmed: 2, skipped: 0 } }
        : undefined,
    )
    renderRoute('/admin/entries')
    const view = await main()

    const muster = within(await view.findByRole('region', { name: 'Muster' }))
    fireEvent.click(muster.getByRole('checkbox', { name: texts.admin.entries.select('14.10.2026, 1 h') }))
    fireEvent.click(muster.getByRole('checkbox', { name: texts.admin.entries.select('14.10.2026, 2 h') }))
    fireEvent.click(view.getByRole('button', { name: texts.admin.entries.confirmSelected(2) }))

    expect(await view.findByText(texts.admin.entries.result(2, 0))).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/admin/entries/confirm',
      body: { ids: ['e1', 'e3'] },
    })
  })

  it('confirms all open entries of a family', async () => {
    const calls = api((call) =>
      call.method === 'POST' ? { status: 200, body: { confirmed: 2, skipped: 0 } } : undefined,
    )
    renderRoute('/admin/entries')
    const view = await main()

    fireEvent.click(await view.findByRole('button', { name: texts.admin.entries.confirmFamily }))

    await vi.waitFor(() => {
      expect(calls).toContainEqual({
        method: 'POST',
        url: '/api/v1/admin/entries/confirm',
        body: { ids: ['e1', 'e3'] },
      })
    })
  })

  it('confirms a single entry', async () => {
    const calls = api((call) =>
      call.method === 'POST' ? { status: 200, body: { ...entries[1], status: 'CONFIRMED' } } : undefined,
    )
    renderRoute('/admin/entries?family=family-2')
    const view = await main()

    const group = within(await view.findByRole('region', { name: 'Müller' }))
    fireEvent.click(group.getByRole('button', { name: texts.admin.entries.confirm }))

    expect(await view.findByText(texts.admin.entries.confirmed)).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'POST', url: '/api/v1/admin/entries/e2/confirm', body: undefined })
    expect(calls.some((call) => call.url.includes('familyId=family-2'))).toBe(true)
  })

  it('shows the number of open entries on the dashboard', async () => {
    stubApi(({ url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (url === '/api/v1/admin/dashboard') {
        return { status: 200, body: { openEntryCount: 3, unassignedUsers: [], recentAutoAssignments: [] } }
      }
      return url.startsWith('/api/v1/admin/entries') ? { status: 200, body: page(entries) } : undefined
    })

    renderRoute('/admin')
    const view = await main()

    expect(await view.findByText(texts.admin.entries.widgetCount(3))).toBeInTheDocument()
    expect(view.getByRole('link', { name: texts.admin.entries.toList })).toHaveAttribute('href', '/admin/entries')
  })
})
