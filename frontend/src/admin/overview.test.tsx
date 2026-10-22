import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { OverviewPage, SchoolYear } from '../api/generated/model'
import { texts } from '../i18n/de'
import { type ApiCall, familyMember, problem, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const admin = { ...familyMember, role: 'ADMIN' as const, assignedToFamily: false }
const year: SchoolYear = {
  id: 'year-1',
  label: '2026/27',
  startsOn: '2026-09-01',
  endsOn: '2027-08-31',
  requiredMinutes: 600,
  closed: false,
  current: true,
}
const earlier: SchoolYear = { ...year, id: 'year-0', label: '2025/26', current: false, closed: true }

const overview: OverviewPage = {
  schoolYear: year,
  requiredMinutes: 600,
  familyCount: 12,
  missingCount: 4,
  content: [
    {
      familyId: 'family-1',
      familyLabel: 'Müller',
      children: [{ id: 'child-1', firstName: 'Anna', lastName: 'Müller' }],
      memberCount: 2,
      confirmedMinutes: 270,
      openMinutes: 90,
      missingMinutes: 330,
    },
    {
      familyId: 'family-2',
      familyLabel: 'Sommer',
      children: [{ id: 'child-2', firstName: 'Lotta', lastName: 'Sommer' }],
      memberCount: 1,
      confirmedMinutes: 600,
      openMinutes: 0,
      missingMinutes: 0,
    },
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 1,
}

function api(handle: (call: ApiCall) => { status: number; body?: unknown } | undefined = () => undefined) {
  return stubApi((call) => {
    if (call.url === '/api/v1/me') {
      return { status: 200, body: admin }
    }
    if (call.url.startsWith('/api/v1/admin/overview')) {
      return { status: 200, body: overview }
    }
    if (call.url === '/api/v1/admin/school-years') {
      return { status: 200, body: [year, earlier] }
    }
    if (call.url === '/api/v1/admin/dashboard') {
      return { status: 200, body: { openEntryCount: 0, unassignedUsers: [], recentAutoAssignments: [] } }
    }
    return handle(call) ?? { status: 200, body: { ...overview, content: [] } }
  })
}

async function main() {
  return within(await screen.findByRole('main', {}, { timeout: 10_000 }))
}

const hint = texts.admin.overview.hint(4, 12, '2026/27', '10 h')

describe('admin overview (FS-ADMOV)', () => {
  it.each(['/admin', '/admin/entries', '/admin/children', '/admin/users', '/admin/school-years'])(
    'shows the hint bar on %s (AC-OV-06)',
    async (path) => {
      api()

      renderRoute(path)
      const view = await main()

      expect(await view.findByRole('link', { name: hint })).toHaveAttribute(
        'href',
        '/admin/families?onlyMissing=true',
      )
    },
  )

  it('opens the families that still miss hours', async () => {
    const calls = api()
    const router = renderRoute('/admin')
    const view = await main()

    fireEvent.click(await view.findByRole('link', { name: hint }))

    await vi.waitFor(() => {
      expect(router.state.location.search).toBe('?onlyMissing=true')
    })
    expect(await view.findByRole('checkbox', { name: texts.admin.overview.onlyMissing })).toBeChecked()
    expect(calls.some((call) => call.url.includes('onlyMissing=true') && call.url.includes('sort='))).toBe(true)
  })

  it('lists families with confirmed, open and missing hours', async () => {
    api()

    renderRoute('/admin/families')
    const view = await main()

    const table = within(await view.findByRole('table', { name: texts.admin.families.title }))
    expect(table.getByRole('link', { name: 'Müller' })).toHaveAttribute('href', '/admin/families/family-1')
    expect(table.getByText(texts.admin.overview.missingChip('5 h 30 min'))).toBeInTheDocument()
    expect(table.getByText(texts.admin.overview.waiting('1 h 30 min'))).toBeInTheDocument()
    expect(table.getByText(texts.admin.overview.fulfilled)).toBeInTheDocument()
    expect(table.getByRole('columnheader', { name: new RegExp(texts.admin.overview.missing) })).toHaveAttribute(
      'aria-sort',
      'descending',
    )
  })

  it('sorts by a column and keeps it in the URL (UI-11)', async () => {
    const calls = api()
    const router = renderRoute('/admin/families')
    const view = await main()
    const table = within(await view.findByRole('table', { name: texts.admin.families.title }))

    fireEvent.click(table.getByRole('button', { name: new RegExp(`^${texts.admin.families.family}`) }))

    await vi.waitFor(() => {
      expect(router.state.location.search).toBe('?sort=familyLabel%2Casc')
    })
    expect(calls.some((call) => call.url.includes('sort=familyLabel%2Casc'))).toBe(true)

    const again = within(await view.findByRole('table', { name: texts.admin.families.title }))
    fireEvent.click(again.getByRole('button', { name: new RegExp(`^${texts.admin.families.family}`) }))
    await vi.waitFor(() => {
      expect(router.state.location.search).toBe('?sort=familyLabel%2Cdesc')
    })
  })

  it('switches the school year for the list and the hint bar', async () => {
    const calls = api()
    const router = renderRoute('/admin/families')
    const view = await main()

    await view.findByRole('option', { name: '2025/26' })
    fireEvent.change(view.getByLabelText(texts.admin.overview.schoolYear), { target: { value: 'year-0' } })

    await vi.waitFor(() => {
      expect(router.state.location.search).toBe('?year=year-0')
    })
    expect(calls.some((call) => call.url === '/api/v1/admin/overview?schoolYearId=year-0&size=1')).toBe(true)
  })

  it('points to the school years when there is none', async () => {
    stubApi(({ url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (url.startsWith('/api/v1/admin/overview')) {
        return problem(404, 'NO_CURRENT_SCHOOL_YEAR')
      }
      return url === '/api/v1/admin/dashboard'
        ? { status: 200, body: { openEntryCount: 0, unassignedUsers: [], recentAutoAssignments: [] } }
        : undefined
    })

    renderRoute('/admin')
    const view = await main()

    expect(await view.findByRole('link', { name: texts.admin.overview.noYear })).toHaveAttribute(
      'href',
      '/admin/school-years',
    )
  })
})
