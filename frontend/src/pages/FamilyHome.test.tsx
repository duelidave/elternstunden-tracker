import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FamilyEntry, FamilyOverview } from '../api/generated/model'
import { texts } from '../i18n/de'
import { type ApiCall, familyMember, problem, stubApi } from '../test/api'
import { renderRoute } from '../test/render'
import { formatDuration, todayInBerlin } from '../time'

const OVERVIEW = '/api/v1/me/family/overview'
const ENTRIES = '/api/v1/me/family/entries'

const openEntry: FamilyEntry = {
  id: 'entry-1',
  workDate: '2026-10-14',
  durationMinutes: 60,
  description: 'Laternen gebastelt',
  status: 'OPEN',
  createdByDisplayName: 'Mia Muster',
  canEdit: true,
  canDelete: true,
  version: 2,
}

/** The text field; its label also says that it is required. */
function descriptionOf(sheet: Pick<typeof screen, 'getByRole'>) {
  return sheet.getByRole('textbox', { name: (name: string) => name.startsWith(texts.entry.description) })
}

const year = { id: 'year-1', label: '2026/27', startsOn: '2026-09-01', endsOn: '2027-08-31', closed: false }

function overview(changes: Partial<FamilyOverview> = {}): FamilyOverview {
  return {
    schoolYear: year,
    schoolYears: [year],
    obligated: true,
    requiredMinutes: 600,
    confirmedMinutes: 270,
    openMinutes: 60,
    remainingMinutes: 330,
    fulfilled: false,
    canAddEntries: true,
    children: [{ firstName: 'Mila' }],
    members: [{ displayName: 'Mia Muster', isCurrentUser: true }],
    partnerHint: false,
    entries: [openEntry, { ...openEntry, id: 'entry-2', status: 'CONFIRMED', canEdit: false, canDelete: false }],
    ...changes,
  }
}

function family(data: FamilyOverview, handle: (call: ApiCall) => { status: number; body?: unknown } | undefined = () => undefined) {
  return stubApi((call) => {
    if (call.url === '/api/v1/me') {
      return { status: 200, body: familyMember }
    }
    if (call.method === 'GET' && call.url.startsWith(OVERVIEW)) {
      return { status: 200, body: data }
    }
    return handle(call)
  })
}

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('formatDuration', () => {
  it.each([
    [45, '45 min'],
    [60, '1 h'],
    [135, '2 h 15 min'],
    [600, '10 h'],
  ])('shows %i minutes as %s, never decimal hours (7.3 rule 4)', (minutes, text) => {
    expect(formatDuration(minutes)).toBe(text)
  })
})

describe('FamilyHome', () => {
  it('shows confirmed and waiting hours separately (UI-07)', async () => {
    family(overview())

    renderRoute('/')

    expect(await screen.findByText(texts.family.progress('4 h 30 min', '10 h'))).toBeInTheDocument()
    expect(screen.getByText(texts.family.waiting('1 h'))).toBeInTheDocument()
    expect(screen.getAllByText(texts.family.status.OPEN)).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: texts.family.delete })).toHaveLength(1)
  })

  it('celebrates fulfilled hours', async () => {
    family(overview({ confirmedMinutes: 600, openMinutes: 0, remainingMinutes: 0, fulfilled: true }))

    renderRoute('/')

    expect(await screen.findByText(texts.family.fulfilled('10 h'))).toBeInTheDocument()
  })

  it('invites to the first entry (UI-08)', async () => {
    family(overview({ entries: [], openMinutes: 0 }))

    renderRoute('/')

    expect(await screen.findByText(texts.family.empty)).toBeInTheDocument()
  })

  it('enters "1 h, today" in four interactions (UI-04, AC-UI-01)', async () => {
    const calls = family(overview(), (call) =>
      call.method === 'POST' && call.url === ENTRIES ? { status: 201, body: openEntry } : undefined,
    )
    renderRoute('/')

    fireEvent.click(await screen.findByRole('link', { name: texts.family.addEntry }))
    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.click(sheet.getByRole('button', { name: '1 h' }))
    fireEvent.change(descriptionOf(sheet), { target: { value: 'Garten aufgeräumt' } })
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await screen.findByText(texts.family.saved)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: ENTRIES,
      body: { workDate: todayInBerlin(), durationMinutes: 60, description: 'Garten aufgeräumt' },
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('checks the entry like the server does (UI-08)', async () => {
    const calls = family(overview())
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.change(descriptionOf(sheet), { target: { value: '  ab  ' } })
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await sheet.findByText(texts.fields.duration)).toBeInTheDocument()
    expect(sheet.getByText(texts.fields.descriptionShort)).toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(0)
  })

  it('marks the empty description red and counts the characters (D-09)', async () => {
    family(overview())
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    const field = descriptionOf(sheet)
    expect(field).toBeRequired()
    expect(field).toHaveAttribute('maxLength', '255')
    expect(sheet.getByText(texts.fields.counter(0, 255))).toBeInTheDocument()

    fireEvent.click(sheet.getByRole('button', { name: '1 h' }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await sheet.findByText(texts.fields.description)).toBeInTheDocument()
    expect(field).toHaveAttribute('aria-invalid', 'true')
    fireEvent.change(field, { target: { value: 'Garten' } })
    expect(await sheet.findByText(texts.fields.counter(6, 255))).toBeInTheDocument()
    await vi.waitFor(() => {
      expect(field).toHaveAttribute('aria-invalid', 'false')
    })
  })

  it.each([
    ['9', '09:00'],
    ['9:00', '09:00'],
    ['9.30', '09:30'],
    ['930', '09:30'],
    ['1530', '15:30'],
  ])('reads the time %s as %s (UI-12)', async (typed, time) => {
    const calls = family(overview(), (call) =>
      call.method === 'POST' && call.url === ENTRIES ? { status: 201, body: openEntry } : undefined,
    )
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.otherDuration }))
    const timeField = sheet.getByLabelText(texts.entry.startTime)
    fireEvent.change(timeField, { target: { value: typed } })
    fireEvent.blur(timeField)
    expect(timeField).toHaveValue(time)
    fireEvent.change(descriptionOf(sheet), { target: { value: 'Laternenfest' } })
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await screen.findByText(texts.family.saved)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: ENTRIES,
      body: { workDate: todayInBerlin(), durationMinutes: 60, description: 'Laternenfest', startTime: time },
    })
  })

  it.each(['24', '9:60', '12345', 'abc'])('rejects %s as time', async (typed) => {
    family(overview())
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.otherDuration }))
    const timeField = sheet.getByLabelText(texts.entry.startTime)
    fireEvent.change(timeField, { target: { value: typed } })
    fireEvent.blur(timeField)

    expect(await sheet.findByText(texts.fields.time)).toBeInTheDocument()
    expect(timeField).toHaveValue(typed)
  })

  it('enters an exact time and duration', async () => {
    const calls = family(overview(), (call) =>
      call.method === 'POST' && call.url === ENTRIES ? { status: 201, body: openEntry } : undefined,
    )
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.otherDuration }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.more }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.more }))
    fireEvent.change(sheet.getByLabelText(texts.entry.startTime), { target: { value: '15' } })
    fireEvent.change(sheet.getByLabelText(texts.entry.startTime), { target: { value: '15:30' } })
    fireEvent.change(descriptionOf(sheet), { target: { value: 'Laternenfest' } })
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await screen.findByText(texts.family.saved)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: ENTRIES,
      body: { workDate: todayInBerlin(), durationMinutes: 90, description: 'Laternenfest', startTime: '15:30' },
    })
  })

  it('explains that only the running school year can be booked (D-29)', async () => {
    family(overview(), (call) =>
      call.method === 'POST' ? problem(400, 'DATE_OUTSIDE_CURRENT_SCHOOL_YEAR') : undefined,
    )
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.click(sheet.getByRole('button', { name: '1 h' }))
    fireEvent.change(descriptionOf(sheet), { target: { value: 'Nachtrag' } })
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await sheet.findByRole('alert')).toHaveTextContent(texts.errors.DATE_OUTSIDE_CURRENT_SCHOOL_YEAR)
  })

  it('shows a duplicate in plain words', async () => {
    family(overview(), (call) => (call.method === 'POST' ? problem(409, 'DUPLICATE_ENTRY') : undefined))
    renderRoute('/entries/new')

    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.title }))
    fireEvent.click(sheet.getByRole('button', { name: '30 min' }))
    fireEvent.change(descriptionOf(sheet), { target: { value: 'Noch einmal' } })
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.submit }))

    expect(await sheet.findByRole('alert')).toHaveTextContent(texts.errors.DUPLICATE_ENTRY)
  })

  it('changes an open entry with its version', async () => {
    const calls = family(overview(), (call) =>
      call.method === 'PATCH' ? { status: 200, body: { ...openEntry, durationMinutes: 120 } } : undefined,
    )
    renderRoute('/')

    fireEvent.click(await screen.findByRole('link', { name: texts.family.edit }))
    const sheet = within(await screen.findByRole('dialog', { name: texts.entry.editTitle }))
    expect(sheet.getByRole('button', { name: '1 h' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(sheet.getByRole('button', { name: '2 h' }))
    fireEvent.click(sheet.getByRole('button', { name: texts.entry.save }))

    expect(await screen.findByText(texts.family.updated)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'PATCH',
      url: `${ENTRIES}/entry-1`,
      body: {
        workDate: '2026-10-14',
        durationMinutes: 120,
        description: 'Laternen gebastelt',
        startTime: '',
        version: 2,
      },
    })
  })

  it('deletes an open entry after asking', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const calls = family(overview(), (call) => (call.method === 'DELETE' ? { status: 204 } : undefined))
    renderRoute('/')

    fireEvent.click(await screen.findByRole('button', { name: texts.family.delete }))

    expect(await screen.findByText(texts.family.deleted)).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'DELETE', url: `${ENTRIES}/entry-1`, body: undefined })
  })

  it('offers no entering in a read-only school year', async () => {
    const earlier = { ...year, id: 'year-0', label: '2025/26', closed: true }
    family(overview({ schoolYear: earlier, schoolYears: [year, earlier], canAddEntries: false, entries: [] }))

    renderRoute('/?year=year-0')

    expect(await screen.findByText(texts.family.readOnly)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: texts.family.addEntry })).not.toBeInTheDocument()
    expect(screen.getByRole('combobox')).toHaveValue('year-0')
  })

  it('hides the partner hint for good (7.3 rule 6)', async () => {
    family(overview({ partnerHint: true }))
    renderRoute('/')

    expect(await screen.findByText(texts.family.partnerHint('Mila'))).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: texts.family.dismissHint }))

    expect(screen.queryByText(texts.family.partnerHint('Mila'))).not.toBeInTheDocument()
    expect(localStorage.getItem('elternstunden.partnerHintDismissed')).toBe('true')
  })

  it('says when there is no school year yet', async () => {
    stubApi(({ url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: familyMember }
      }
      return url.startsWith(OVERVIEW) ? problem(404, 'NO_CURRENT_SCHOOL_YEAR') : undefined
    })

    renderRoute('/')

    expect(await screen.findByText(texts.family.noSchoolYear)).toBeInTheDocument()
  })
})
