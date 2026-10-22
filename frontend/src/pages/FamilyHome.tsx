import { useState } from 'react'
import { Link, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { errorText, hasCode } from '../api/errors'
import { useDeleteFamilyEntry } from '../api/generated/endpoints/my-family/my-family'
import type { FamilyEntry, FamilyOverview, Me } from '../api/generated/model'
import { texts } from '../i18n/de'
import { formatDate, formatDuration } from '../time'
import { useFamilyOverview, usePartnerHintDismissed, useRefreshFamily } from './familyData'

/** What the entry sheet tells the overview after closing (UI-04: "Eingetragen. Wartet auf Bestätigung."). */
export interface NoticeState {
  notice?: string
}

/**
 * Family overview (FS-FAM 7.3): progress, the button "Stunden eintragen", partner hint and entries. The entry sheet
 * (/entries/new, /entries/:entryId) opens on top of it.
 */
export function FamilyHome({ me }: { me: Me }) {
  const overview = useFamilyOverview()
  const location = useLocation()
  const notice = (location.state as NoticeState | null)?.notice

  if (overview.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (overview.isError) {
    return (
      <section className="card">
        <h2>{texts.home.greeting(me.displayName)}</h2>
        <p className={hasCode(overview.error, 'NO_CURRENT_SCHOOL_YEAR') ? 'text-muted' : 'alert'} role="alert">
          {hasCode(overview.error, 'NO_CURRENT_SCHOOL_YEAR') ? texts.family.noSchoolYear : errorText(overview.error)}
        </p>
      </section>
    )
  }
  const data = overview.data
  return (
    <>
      <section className="card">
        <div className="card__header">
          <h2>{texts.home.greeting(me.displayName)}</h2>
          <SchoolYearSelect overview={data} />
        </div>
        <Progress overview={data} />
        {!data.obligated && <p className="text-muted">{texts.family.notObligated}</p>}
        {!data.canAddEntries && (
          <p className="text-muted">{data.schoolYear.closed || data.schoolYears[0]?.id !== data.schoolYear.id
            ? texts.family.readOnly
            : texts.family.noCurrentYear}</p>
        )}
        <p aria-live="polite" className="m-0">
          {notice !== undefined && <span className="notice mt-4 block">{notice}</span>}
        </p>
        <p className="mb-0">
          <Link className="inline-flex min-h-tap items-center" to="/family">
            {texts.family.toFamily}
          </Link>
        </p>
      </section>
      <PartnerHint overview={data} />
      <Entries overview={data} />
      {data.canAddEntries && (
        <div className="sticky-action">
          <Link className="button w-full" to={{ pathname: '/entries/new', search: location.search }}>
            {texts.family.addEntry}
          </Link>
        </div>
      )}
      <Outlet context={data} />
    </>
  )
}

/** UI-07: confirmed as a full bar, open ones lighter on top; only confirmed minutes count (D-10). */
function Progress({ overview }: { overview: FamilyOverview }) {
  const { requiredMinutes, confirmedMinutes, openMinutes, fulfilled } = overview
  const required = Math.max(requiredMinutes, 1)
  const confirmedShare = Math.min(confirmedMinutes / required, 1) * 100
  const openShare = Math.min(openMinutes / required, 1 - confirmedShare / 100) * 100
  return (
    <div className="progress">
      <div className="progress__bar" aria-hidden="true">
        <div className="progress__confirmed" style={{ width: `${String(confirmedShare)}%` }} />
        <div className="progress__open" style={{ width: `${String(openShare)}%` }} />
      </div>
      {fulfilled ? (
        <p className="progress__text font-bold">{texts.family.fulfilled(formatDuration(requiredMinutes))}</p>
      ) : (
        <p className="progress__text font-bold">
          {texts.family.progress(formatDuration(confirmedMinutes), formatDuration(requiredMinutes))}
        </p>
      )}
      {openMinutes > 0 && <p className="m-0 text-muted">{texts.family.waiting(formatDuration(openMinutes))}</p>}
    </div>
  )
}

function SchoolYearSelect({ overview }: { overview: FamilyOverview }) {
  const [params, setParams] = useSearchParams()
  if (overview.schoolYears.length < 2) {
    return <span className="chip chip--muted">{overview.schoolYear.label}</span>
  }
  const current = overview.schoolYears[0]?.id
  return (
    <label className="year-select">
      <span className="sr-only">{texts.family.schoolYear}</span>
      <select
        value={overview.schoolYear.id}
        onChange={(event) => {
          const next = new URLSearchParams(params)
          if (event.target.value === current) {
            next.delete('year')
          } else {
            next.set('year', event.target.value)
          }
          setParams(next)
        }}
      >
        {overview.schoolYears.map((year) => (
          <option key={year.id} value={year.id}>
            {year.label}
          </option>
        ))}
      </select>
    </label>
  )
}

export function PartnerHint({ overview }: { overview: FamilyOverview }) {
  const [dismissed, dismiss] = usePartnerHintDismissed()
  const child = overview.children[0]?.firstName
  if (!overview.partnerHint || dismissed || child === undefined) {
    return null
  }
  return (
    <section className="card hint">
      <p className="mt-0">{texts.family.partnerHint(child)}</p>
      <button type="button" className="button button--secondary" onClick={dismiss}>
        {texts.family.dismissHint}
      </button>
    </section>
  )
}

function Entries({ overview }: { overview: FamilyOverview }) {
  const [message, setMessage] = useState<string | undefined>()
  const remove = useDeleteFamilyEntry()
  const refresh = useRefreshFamily()
  const navigate = useNavigate()
  const location = useLocation()

  const deleteEntry = async (entry: FamilyEntry) => {
    if (!window.confirm(texts.family.confirmDelete)) {
      return
    }
    try {
      await remove.mutateAsync({ entryId: entry.id })
      setMessage(texts.family.deleted)
      await navigate({ pathname: '/', search: location.search }, { replace: true, state: {} })
    } catch {
      // shown below
    }
    await refresh()
  }

  return (
    <section className="card">
      <h2>{texts.family.entriesTitle}</h2>
      <p aria-live="polite" className="m-0">
        {message !== undefined && <span className="notice block">{message}</span>}
      </p>
      {remove.isError && (
        <p className="alert" role="alert">
          {errorText(remove.error)}
        </p>
      )}
      {overview.entries.length === 0 ? (
        <p className="text-muted">{overview.canAddEntries ? texts.family.empty : texts.family.emptyReadOnly}</p>
      ) : (
        <ul className="stack mt-4">
          {overview.entries.map((entry) => (
            <li key={entry.id} className="entry">
              <div className="entry__head">
                <span className="font-bold">
                  {formatDate(entry.workDate)} · {formatDuration(entry.durationMinutes)}
                </span>
                <span className={entry.status === 'CONFIRMED' ? 'status status--confirmed' : 'status'}>
                  {texts.family.status[entry.status]}
                </span>
              </div>
              <p className="entry__text">{entry.description}</p>
              <p className="m-0 text-sm text-muted">
                {texts.family.by(entry.createdByDisplayName)}
                {entry.startTime !== undefined && ` · ${texts.family.at(entry.startTime)}`}
              </p>
              {(entry.canEdit || entry.canDelete) && (
                <div className="actions mt-2">
                  {entry.canEdit && (
                    <Link
                      className="button button--secondary"
                      to={{ pathname: `/entries/${entry.id}`, search: location.search }}
                    >
                      {texts.family.edit}
                    </Link>
                  )}
                  {entry.canDelete && (
                    <button
                      type="button"
                      className="button button--danger"
                      disabled={remove.isPending}
                      onClick={() => void deleteEntry(entry)}
                    >
                      {texts.family.delete}
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
