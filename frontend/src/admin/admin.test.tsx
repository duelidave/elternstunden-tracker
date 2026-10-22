import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AdminChild, AdminDashboard, AdminUserDetail, SchoolYear } from '../api/generated/model'
import { texts } from '../i18n/de'
import { familyMember, stubApi } from '../test/api'
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
const child: AdminChild = {
  id: 'child-1',
  firstName: 'Anna',
  lastName: 'Müller',
  birthDate: '2017-05-03',
  familyId: 'family-1',
  familyLabel: 'Müller',
  enrolledSchoolYearIds: [],
}
const unassignedUser: AdminUserDetail = {
  user: {
    id: 'user-1',
    displayName: 'Uli Unzugeordnet',
    email: 'uli@example.org',
    role: 'FAMILY_MEMBER',
    status: 'ACTIVE',
    managedByConfig: false,
    createdAt: '2026-10-01T10:00:00Z',
  },
  claim: {
    id: 'claim-1',
    firstName: 'Anna',
    lastName: 'Mueler',
    birthDate: '2017-05-03',
    matchResult: 'NO_MATCH',
    createdAt: '2026-10-01T10:00:00Z',
  },
  candidates: [
    {
      childId: 'child-1',
      firstName: 'Anna',
      lastName: 'Müller',
      birthDate: '2017-05-03',
      familyId: 'family-1',
      familyLabel: 'Müller',
    },
  ],
}
const dashboard: AdminDashboard = {
  unassignedUsers: [unassignedUser],
  recentAutoAssignments: [
    {
      userId: 'user-2',
      displayName: 'Paula Partner',
      familyId: 'family-2',
      familyLabel: 'Sommer',
      assignedAt: '2026-10-20T08:30:00Z',
    },
  ],
}

const page = (content: unknown[]) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })

/** The admin chunk loads lazily, so the layout appears only after the first navigation resolved. */
async function main() {
  return within(await screen.findByRole('main', {}, { timeout: 3000 }))
}

describe('admin area', () => {
  afterEach(() => {
    window.history.replaceState(null, '', '/')
  })

  it('sends family members back to the start page', async () => {
    stubApi(({ url }) => (url === '/api/v1/me' ? { status: 200, body: familyMember } : undefined))

    const router = renderRoute('/admin')

    expect(await screen.findByText(texts.home.family)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('shows unassigned users with candidates and assigns with one tap', async () => {
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (url === '/api/v1/admin/dashboard') {
        return { status: 200, body: dashboard }
      }
      if (method === 'PUT' && url === '/api/v1/admin/users/user-1/family') {
        return { status: 200, body: { ...unassignedUser.user, familyId: 'family-1' } }
      }
      return undefined
    })

    renderRoute('/admin')
    const view = await main()

    expect(await view.findByText('Uli Unzugeordnet')).toBeInTheDocument()
    expect(view.getByText(texts.admin.matchResult.NO_MATCH)).toBeInTheDocument()
    expect(view.getByText(texts.admin.dashboard.recentEntry('Paula Partner', 'Sommer'))).toBeInTheDocument()

    fireEvent.click(view.getByRole('button', { name: texts.admin.dashboard.assignTo('Müller') }))

    await vi.waitFor(() => {
      expect(calls).toContainEqual({
        method: 'PUT',
        url: '/api/v1/admin/users/user-1/family',
        body: { familyId: 'family-1' },
      })
    })
  })

  it('lists children and enrolls them in the current school year', async () => {
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (url === '/api/v1/admin/school-years') {
        return { status: 200, body: [year] }
      }
      if (url.startsWith('/api/v1/admin/children?')) {
        return { status: 200, body: page([child]) }
      }
      if (method === 'PUT' && url === '/api/v1/admin/children/child-1/enrollments/year-1') {
        return { status: 204 }
      }
      return undefined
    })

    renderRoute('/admin/children')
    const view = await main()

    const toggle = await view.findByRole('checkbox', { name: texts.admin.children.enrolledIn('2026/27') })
    expect(view.getByRole('link', { name: 'Anna Müller' })).toHaveAttribute('href', '/admin/children/child-1')
    fireEvent.click(toggle)

    await vi.waitFor(() => {
      expect(calls.some((call) => call.method === 'PUT' && call.url.endsWith('/enrollments/year-1'))).toBe(true)
    })
  })

  it('previews a CSV import before running it', async () => {
    const calls = stubApi(({ url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (url.startsWith('/api/v1/admin/children/import')) {
        const dryRun = url.includes('dryRun=true')
        return {
          status: 200,
          body: { dryRun, applied: !dryRun, created: 2, updated: 0, unchanged: 1, errors: [] },
        }
      }
      return undefined
    })
    const csv = 'first_name;last_name;birth_date;family_key;external_ref\nAnna;Müller;2017-05-03;f1;\n'

    renderRoute('/admin/children/import')
    const view = await main()
    const input = await view.findByLabelText(texts.admin.import.file)
    fireEvent.change(input, { target: { files: [new File([csv], 'kinder.csv', { type: 'text/csv' })] } })
    await vi.waitFor(() => {
      expect(view.getByRole('button', { name: texts.admin.import.preview })).toBeEnabled()
    })
    fireEvent.click(view.getByRole('button', { name: texts.admin.import.preview }))

    expect(await view.findByText(texts.admin.import.result(2, 0, 1))).toBeInTheDocument()
    fireEvent.click(view.getByRole('button', { name: texts.admin.import.apply }))

    expect(await view.findByText(texts.admin.import.applied)).toBeInTheDocument()
    expect(calls.filter((call) => call.url.startsWith('/api/v1/admin/children/import')).map((call) => call.url)).toEqual([
      '/api/v1/admin/children/import?dryRun=true',
      '/api/v1/admin/children/import?dryRun=false',
    ])
  })

  it('creates a school year with the required hours as minutes', async () => {
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (method === 'GET' && url === '/api/v1/admin/school-years') {
        return { status: 200, body: [year] }
      }
      if (method === 'POST' && url === '/api/v1/admin/school-years') {
        return { status: 201, body: { ...year, id: 'year-2', label: '2027/28', current: false } }
      }
      return undefined
    })

    renderRoute('/admin/school-years')
    const view = await main()
    const form = within((await view.findByRole('heading', { name: texts.admin.schoolYears.create })).closest('section') as HTMLElement)
    fireEvent.change(form.getByLabelText(texts.admin.schoolYears.label), { target: { value: '2027/28' } })
    fireEvent.change(form.getByLabelText(texts.admin.schoolYears.startsOn), { target: { value: '2027-09-01' } })
    fireEvent.change(form.getByLabelText(texts.admin.schoolYears.endsOn), { target: { value: '2028-08-31' } })
    fireEvent.change(form.getByLabelText(texts.admin.schoolYears.required), { target: { value: '7.5' } })
    fireEvent.click(form.getByRole('button', { name: texts.admin.save }))

    await vi.waitFor(() => {
      expect(calls).toContainEqual({
        method: 'POST',
        url: '/api/v1/admin/school-years',
        body: { label: '2027/28', startsOn: '2027-09-01', endsOn: '2028-08-31', requiredMinutes: 450 },
      })
    })
  })

  it('disables a user only after confirmation', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: admin }
      }
      if (url === '/api/v1/admin/users/user-1') {
        return { status: 200, body: unassignedUser }
      }
      if (url.startsWith('/api/v1/admin/families')) {
        return { status: 200, body: page([]) }
      }
      if (method === 'POST' && url === '/api/v1/admin/users/user-1/disable') {
        return { status: 200, body: { ...unassignedUser.user, status: 'DISABLED' } }
      }
      return undefined
    })

    renderRoute('/admin/users/user-1')
    const view = await main()
    fireEvent.click(await view.findByRole('button', { name: texts.admin.users.disable }))

    expect(confirm).toHaveBeenCalledWith(texts.admin.users.confirmDisable)
    await vi.waitFor(() => {
      expect(calls).toContainEqual({ method: 'POST', url: '/api/v1/admin/users/user-1/disable', body: undefined })
    })
  })
})
