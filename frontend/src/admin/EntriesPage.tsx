import { useState } from 'react'
import { Link } from 'react-router'
import { errorText } from '../api/errors'
import {
  useConfirmEntries,
  useConfirmEntry,
  useDeleteEntry,
  useListEntries,
  useUpdateEntry,
} from '../api/generated/endpoints/entries/entries'
import type { AdminEntry, ListEntriesParams } from '../api/generated/model'
import { DateField } from '../components/DateField'
import { EntryForm, type EntryValues } from '../components/EntryForm'
import { Pager } from '../components/Pager'
import { texts } from '../i18n/de'
import { formatDate, formatDateTime, formatDuration } from '../time'
import { FamilySelect } from './FamilySelect'
import { useInvalidateAll } from './invalidate'
import { useListParams } from './params'

const PAGE_SIZE = 200
// Admins may correct entries of every open school year; the server checks the date (INV-01, INV-04)
const EARLIEST = '2000-01-01'

/**
 * "Zu bestätigen" (FS-CONF 7.5): open entries grouped by family, oldest first; filters in the URL (UI-11). Confirm
 * single entries, all open ones of a family or a selection.
 */
export function EntriesPage() {
  const params = useListParams()
  const status = params.get('status') || 'OPEN'
  const query: ListEntriesParams = {
    ...(status === 'ALL' ? {} : { status: status as 'OPEN' | 'CONFIRMED' }),
    ...(params.get('family') === '' ? {} : { familyId: params.get('family') }),
    ...(params.get('from') === '' ? {} : { from: params.get('from') }),
    ...(params.get('to') === '' ? {} : { to: params.get('to') }),
    page: params.page,
    size: PAGE_SIZE,
  }
  const entries = useListEntries(query)
  const [selected, setSelected] = useState<string[]>([])
  const [message, setMessage] = useState<string | undefined>()
  const confirmMany = useConfirmEntries()
  const invalidate = useInvalidateAll()

  // Each with the version on screen: an entry the family changed meanwhile is skipped (7.5 point 3, AC-CONF-06)
  const confirm = async (ids: string[]) => {
    const shown = entries.data?.content ?? []
    const seen = ids.flatMap((id) => shown.filter((entry) => entry.id === id).map(({ version }) => ({ id, version })))
    try {
      const result = await confirmMany.mutateAsync({ data: { entries: seen } })
      setMessage(texts.admin.entries.result(result.confirmed, result.skipped))
      setSelected([])
    } catch {
      // shown below
    }
    await invalidate()
  }

  const toggle = (id: string) => {
    setSelected((current) => (current.includes(id) ? current.filter((other) => other !== id) : [...current, id]))
  }

  const groups = groupByFamily(entries.data?.content ?? [])
  return (
    <section className="card">
      <h2>{texts.admin.entries.title}</h2>
      <div className="filters filters--entries">
        <div className="field">
          <label htmlFor="entries-status">{texts.admin.entries.status}</label>
          <select
            id="entries-status"
            value={status}
            onChange={(event) => {
              params.set('status', event.target.value === 'OPEN' ? '' : event.target.value)
            }}
          >
            <option value="OPEN">{texts.admin.entries.statuses.OPEN}</option>
            <option value="CONFIRMED">{texts.admin.entries.statuses.CONFIRMED}</option>
            <option value="ALL">{texts.admin.entries.allStatuses}</option>
          </select>
        </div>
        <FamilySelect
          label={texts.admin.entries.family}
          emptyLabel={texts.admin.entries.allFamilies}
          value={params.get('family')}
          onChange={(event) => {
            params.set('family', event.target.value)
          }}
        />
        <DateField
          label={texts.admin.entries.from}
          value={params.get('from')}
          onChange={(value) => {
            if (value === '' || /^\d{4}-\d{2}-\d{2}$/.test(value)) {
              params.set('from', value)
            }
          }}
        />
        <DateField
          label={texts.admin.entries.to}
          value={params.get('to')}
          onChange={(value) => {
            if (value === '' || /^\d{4}-\d{2}-\d{2}$/.test(value)) {
              params.set('to', value)
            }
          }}
        />
      </div>
      <p aria-live="polite" className="m-0">
        {message !== undefined && <span className="notice block">{message}</span>}
      </p>
      {(entries.isError || confirmMany.isError) && (
        <p className="alert" role="alert">
          {errorText(entries.error ?? confirmMany.error)}
        </p>
      )}
      {entries.isPending && <p role="status">{texts.common.loading}</p>}
      {entries.data && groups.length === 0 && (
        <p className="text-muted">{status === 'OPEN' ? texts.admin.entries.empty : texts.admin.entries.emptyFiltered}</p>
      )}
      {groups.map((group) => {
        const confirmable = group.entries.filter(isConfirmable).map((entry) => entry.id)
        return (
          <section key={group.familyId} className="entry-group" aria-labelledby={`family-${group.familyId}`}>
            <div className="card__header">
              <h3 id={`family-${group.familyId}`} className="my-0">
                <Link to={`/admin/families/${group.familyId}`}>
                  {group.familyLabel || texts.admin.families.noLabel}
                </Link>
              </h3>
              {confirmable.length > 1 && (
                <button
                  type="button"
                  className="button button--secondary"
                  disabled={confirmMany.isPending}
                  onClick={() => void confirm(confirmable)}
                >
                  {texts.admin.entries.confirmFamily}
                </button>
              )}
            </div>
            <ul className="stack mt-3">
              {group.entries.map((entry) => (
                <AdminEntryItem
                  key={entry.id}
                  entry={entry}
                  selected={selected.includes(entry.id)}
                  onToggle={toggle}
                  onMessage={setMessage}
                />
              ))}
            </ul>
          </section>
        )
      })}
      {entries.data && (
        <Pager
          page={entries.data.page}
          totalPages={entries.data.totalPages}
          totalElements={entries.data.totalElements}
          onPage={(page) => {
            params.set('page', String(page))
          }}
        />
      )}
      {selected.length > 0 && (
        <div className="sticky-action">
          <button
            type="button"
            className="button w-full"
            disabled={confirmMany.isPending}
            onClick={() => void confirm(selected)}
          >
            {texts.admin.entries.confirmSelected(selected.length)}
          </button>
        </div>
      )}
    </section>
  )
}

interface Group {
  familyId: string
  familyLabel: string
  entries: AdminEntry[]
}

/** Families in the order of their oldest entry (7.5 point 1). */
function groupByFamily(entries: AdminEntry[]): Group[] {
  const groups = new Map<string, Group>()
  for (const entry of entries) {
    const group = groups.get(entry.familyId) ?? { familyId: entry.familyId, familyLabel: entry.familyLabel, entries: [] }
    group.entries.push(entry)
    groups.set(entry.familyId, group)
  }
  return [...groups.values()]
}

function isConfirmable(entry: AdminEntry): boolean {
  return entry.status === 'OPEN' && !entry.schoolYearClosed
}

interface AdminEntryItemProps {
  entry: AdminEntry
  /** Without these the entry cannot be selected, e.g. in the family detail. */
  selected?: boolean
  onToggle?: (id: string) => void
  onMessage: (message: string) => void
}

/** One entry for admins: confirm, correct (INV-07) or delete (7.5 point 5). */
export function AdminEntryItem({ entry, selected, onToggle, onMessage }: AdminEntryItemProps) {
  const [editing, setEditing] = useState(false)
  const confirmOne = useConfirmEntry()
  const update = useUpdateEntry()
  const remove = useDeleteEntry()
  const invalidate = useInvalidateAll()
  const summary = `${formatDate(entry.workDate)}, ${formatDuration(entry.durationMinutes)}`
  const editable = !entry.schoolYearClosed

  const run = async (action: () => Promise<unknown>, message: string) => {
    let done = false
    try {
      await action()
      onMessage(message)
      done = true
    } catch {
      // shown below
    }
    await invalidate()
    return done
  }

  const save = async (values: EntryValues) => {
    const data = { ...values, startTime: values.startTime ?? '', version: entry.version }
    if (await run(() => update.mutateAsync({ entryId: entry.id, data }), texts.admin.entries.saved)) {
      setEditing(false)
    }
  }

  const error = confirmOne.error ?? update.error ?? remove.error
  return (
    <li className="entry">
      <div className="entry__head">
        <span className="flex items-center gap-3">
          {onToggle !== undefined && isConfirmable(entry) && (
            <label className="entry__select">
              <input
                type="checkbox"
                checked={selected === true}
                aria-label={texts.admin.entries.select(summary)}
                onChange={() => {
                  onToggle(entry.id)
                }}
              />
            </label>
          )}
          <span className="font-bold">
            {formatDate(entry.workDate)} · {formatDuration(entry.durationMinutes)}
            {entry.startTime !== undefined && ` · ${texts.family.at(entry.startTime)}`}
          </span>
        </span>
        <span className={entry.status === 'CONFIRMED' ? 'status status--confirmed' : 'status'}>
          {entry.schoolYearClosed ? texts.admin.entries.closedYear : texts.admin.entries.statuses[entry.status]}
        </span>
      </div>
      <p className="entry__text">{entry.description}</p>
      <p className="m-0 text-sm text-muted">
        {texts.admin.entries.createdBy(entry.createdByDisplayName, formatDateTime(entry.createdAt))}
        {entry.confirmedByDisplayName !== undefined && ` · ${texts.admin.entries.confirmedBy(entry.confirmedByDisplayName)}`}
      </p>
      {error !== null && (
        <p className="alert mt-2" role="alert">
          {errorText(error)}
        </p>
      )}
      {editable && !editing && (
        <div className="actions actions--compact mt-2">
          {entry.status === 'OPEN' && (
            <button
              type="button"
              className="button"
              disabled={confirmOne.isPending}
              onClick={() =>
                void run(
                  () => confirmOne.mutateAsync({ entryId: entry.id, data: { version: entry.version } }),
                  texts.admin.entries.confirmed,
                )
              }
            >
              {texts.admin.entries.confirm}
            </button>
          )}
          <button
            type="button"
            className="button button--secondary"
            onClick={() => {
              setEditing(true)
            }}
          >
            {texts.admin.entries.edit}
          </button>
          <button
            type="button"
            className="button button--danger"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(texts.admin.entries.confirmDelete)) {
                void run(() => remove.mutateAsync({ entryId: entry.id }), texts.admin.entries.deleted)
              }
            }}
          >
            {texts.admin.entries.delete}
          </button>
        </div>
      )}
      {editing && (
        <div className="panel mt-3">
          {entry.status === 'CONFIRMED' && <p className="mt-0 text-muted">{texts.admin.entries.reopenHint}</p>}
          <EntryForm
            initial={{
              workDate: entry.workDate,
              durationMinutes: entry.durationMinutes,
              description: entry.description,
              ...(entry.startTime === undefined ? {} : { startTime: entry.startTime }),
            }}
            minDate={EARLIEST}
            submitLabel={texts.admin.save}
            onSubmit={save}
          />
          <button
            type="button"
            className="button button--secondary mt-3 w-full"
            onClick={() => {
              setEditing(false)
            }}
          >
            {texts.admin.cancel}
          </button>
        </div>
      )}
    </li>
  )
}
