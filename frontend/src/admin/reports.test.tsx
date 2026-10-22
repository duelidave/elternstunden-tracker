import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SchoolYear } from '../api/generated/model'
import { texts } from '../i18n/de'
import { familyMember, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const admin = { ...familyMember, role: 'ADMIN' as const, assignedToFamily: false }
const current: SchoolYear = {
  id: 'year-1',
  label: '2026/27',
  startsOn: '2026-09-01',
  endsOn: '2099-08-31',
  requiredMinutes: 600,
  closed: false,
  current: true,
}
const previous: SchoolYear = { ...current, id: 'year-0', label: '2025/26', endsOn: '2026-08-31', current: false }

function api() {
  return stubApi(({ url }) => {
    if (url === '/api/v1/me') {
      return { status: 200, body: admin }
    }
    if (url === '/api/v1/admin/school-years') {
      return { status: 200, body: [current, previous] }
    }
    if (url.startsWith('/api/v1/admin/families')) {
      const family = { id: 'family-1', displayLabel: 'Müller', children: [], memberCount: 1 }
      return { status: 200, body: { content: [family], page: 0, size: 200, totalElements: 1, totalPages: 1 } }
    }
    return undefined
  })
}

async function main() {
  return within(await screen.findByRole('main', {}, { timeout: 10_000 }))
}

describe('billing PDF (FS-PDF)', () => {
  it('downloads the interim total list of the current year', async () => {
    api()

    renderRoute('/admin/reports')
    const view = await main()

    const link = await view.findByRole('link', { name: texts.admin.reports.download })
    expect(link).toHaveAttribute('href', '/api/v1/admin/reports/billing?scope=ALL&schoolYearId=year-1')
    expect(link).toHaveAttribute('download')
    expect(view.getByText(texts.admin.reports.interim)).toBeInTheDocument()
  })

  it('needs a family for the single billing', async () => {
    api()
    renderRoute('/admin/reports')
    const view = await main()

    fireEvent.click(await view.findByRole('radio', { name: texts.admin.reports.family }))

    expect(await view.findByText(texts.admin.reports.chooseFamilyFirst)).toBeInTheDocument()
    await view.findByRole('option', { name: 'Müller' })
    fireEvent.change(view.getByLabelText(texts.admin.reports.chooseFamily), { target: { value: 'family-1' } })

    expect(await view.findByRole('link', { name: texts.admin.reports.download })).toHaveAttribute(
      'href',
      '/api/v1/admin/reports/billing?scope=FAMILY&schoolYearId=year-1&familyId=family-1',
    )
  })

  it('offers the final billing for a finished year', async () => {
    api()
    renderRoute('/admin/reports?year=year-0')
    const view = await main()

    expect(await view.findByText(texts.admin.reports.final)).toBeInTheDocument()
    expect(view.getByRole('link', { name: texts.admin.reports.download })).toHaveAttribute(
      'href',
      '/api/v1/admin/reports/billing?scope=ALL&schoolYearId=year-0',
    )
  })
})
