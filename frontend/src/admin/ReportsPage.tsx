import { useId } from 'react'
import { getGetBillingReportUrl } from '../api/generated/endpoints/reports/reports'
import { useListSchoolYears } from '../api/generated/endpoints/school-years/school-years'
import type { SchoolYear } from '../api/generated/model'
import { texts } from '../i18n/de'
import { todayInBerlin } from '../time'
import { FamilySelect } from './FamilySelect'
import { useListParams } from './params'

/** FS-PDF 7.8: an interim state while the year runs, the final billing once it ended or was closed. */
function finished(year: SchoolYear): boolean {
  return year.closed || year.endsOn < todayInBerlin()
}

/**
 * Billing as PDF (FS-PDF 7.8, D-12): all families or one family, for a school year. The browser downloads the file
 * directly; it is created on demand and never stored.
 */
export function ReportsPage() {
  const params = useListParams()
  const yearId = useId()
  const schoolYears = useListSchoolYears()
  const years = schoolYears.data ?? []
  const year = years.find((entry) => entry.id === params.get('year')) ?? years.find((entry) => entry.current) ?? years[0]
  const scope = params.get('scope') === 'FAMILY' ? 'FAMILY' : 'ALL'
  const familyId = params.get('family')

  if (schoolYears.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (year === undefined) {
    return (
      <section className="card">
        <h2>{texts.admin.reports.title}</h2>
        <p className="text-muted">{texts.admin.overview.noYear}</p>
      </section>
    )
  }
  const ready = scope === 'ALL' || familyId !== ''
  const href = getGetBillingReportUrl({
    scope,
    schoolYearId: year.id,
    ...(scope === 'FAMILY' ? { familyId } : {}),
  })

  return (
    <section className="card">
      <h2>{texts.admin.reports.title}</h2>
      <p>{texts.admin.reports.intro}</p>
      <div className="field">
        <label htmlFor={yearId}>{texts.admin.overview.schoolYear}</label>
        <select
          id={yearId}
          value={year.id}
          onChange={(event) => {
            const current = years.find((entry) => entry.current)?.id
            params.set('year', event.target.value === current ? '' : event.target.value)
          }}
        >
          {years.map((entry) => (
            <option key={entry.id} value={entry.id}>
              {entry.label}
            </option>
          ))}
        </select>
      </div>
      <fieldset>
        <legend>{texts.admin.reports.scope}</legend>
        {(['ALL', 'FAMILY'] as const).map((option) => (
          <label key={option} className="checkbox">
            <input
              type="radio"
              name="scope"
              checked={scope === option}
              onChange={() => {
                params.set('scope', option === 'ALL' ? '' : option)
              }}
            />
            {option === 'ALL' ? texts.admin.reports.all : texts.admin.reports.family}
          </label>
        ))}
      </fieldset>
      {scope === 'FAMILY' && (
        <FamilySelect
          label={texts.admin.reports.chooseFamily}
          value={familyId}
          onChange={(event) => {
            params.set('family', event.target.value)
          }}
        />
      )}
      <p className="notice">{finished(year) ? texts.admin.reports.final : texts.admin.reports.interim}</p>
      {ready ? (
        <a className="button" href={href} download>
          {texts.admin.reports.download}
        </a>
      ) : (
        <p className="text-muted">{texts.admin.reports.chooseFamilyFirst}</p>
      )}
    </section>
  )
}
