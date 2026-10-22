import { useId } from 'react'
import { Link } from 'react-router'
import { errorText } from '../api/errors'
import { useEnrollChild, useListChildren, useUnenrollChild } from '../api/generated/endpoints/children/children'
import { useListSchoolYears } from '../api/generated/endpoints/school-years/school-years'
import type { AdminChild, SchoolYear } from '../api/generated/model'
import { type Column, DataTable } from '../components/DataTable'
import { Pager } from '../components/Pager'
import { SearchField } from '../components/SearchField'
import { texts } from '../i18n/de'
import { formatDate } from '../time'
import { useInvalidateAll } from './invalidate'
import { useListParams } from './params'

/** Children with search, enrollment per school year and paging (FS-ADM 7.7, UI-11). */
export function ChildrenPage() {
  const params = useListParams()
  const yearSelectId = useId()
  const schoolYears = useListSchoolYears()
  const years = schoolYears.data ?? []
  const year = years.find((entry) => entry.id === params.get('year')) ?? years.find((entry) => entry.current) ?? years[0]
  const onlyEnrolled = params.get('enrolled') === '1'
  const children = useListChildren({
    q: params.get('q') || undefined,
    schoolYearId: onlyEnrolled ? year?.id : undefined,
    page: params.page,
  })

  const columns: Column<AdminChild>[] = [
    {
      header: texts.admin.children.name,
      cell: (child) => (
        <Link to={`/admin/children/${child.id}`}>
          {child.firstName} {child.lastName}
        </Link>
      ),
    },
    { header: texts.admin.children.birthDate, cell: (child) => formatDate(child.birthDate) },
    {
      header: texts.admin.children.family,
      cell: (child) => (
        <Link to={`/admin/families/${child.familyId}`}>{child.familyLabel || texts.admin.families.noLabel}</Link>
      ),
    },
    {
      header: year === undefined ? texts.admin.children.enrolled : texts.admin.children.enrolledIn(year.label),
      cell: (child) => (year === undefined ? texts.admin.none : <EnrollmentToggle child={child} year={year} compact />),
    },
  ]

  return (
    <section className="card">
      <div className="card__header">
        <h2>{texts.admin.children.title}</h2>
        <div className="actions mt-0">
          <Link className="button" to="/admin/children/new">
            {texts.admin.children.create}
          </Link>
          <Link className="button button--secondary" to="/admin/children/import">
            {texts.admin.children.import}
          </Link>
        </div>
      </div>
      <div className="filters">
        <SearchField
          value={params.get('q')}
          onSearch={(query) => {
            params.set('q', query)
          }}
        />
        {years.length > 0 && (
          <div className="field">
            <label htmlFor={yearSelectId}>{texts.admin.children.enrollYear}</label>
            <select
              id={yearSelectId}
              value={year?.id ?? ''}
              onChange={(event) => {
                params.set('year', event.target.value)
              }}
            >
              {years.map((entry) => (
                <option key={entry.id} value={entry.id}>
                  {entry.label}
                </option>
              ))}
            </select>
          </div>
        )}
        <div className="checkbox">
          <input
            id={`${yearSelectId}-enrolled`}
            type="checkbox"
            checked={onlyEnrolled}
            onChange={(event) => {
              params.set('enrolled', event.target.checked ? '1' : '')
            }}
          />
          <label htmlFor={`${yearSelectId}-enrolled`}>{texts.admin.children.onlyEnrolled}</label>
        </div>
      </div>
      {schoolYears.isSuccess && years.length === 0 && <p className="notice">{texts.admin.children.noSchoolYear}</p>}
      {children.isError && (
        <p className="alert" role="alert">
          {errorText(children.error)}
        </p>
      )}
      {children.data && (
        <>
          <DataTable
            caption={texts.admin.children.title}
            columns={columns}
            rows={children.data.content}
            rowKey={(child) => child.id}
            empty={texts.admin.empty}
          />
          <Pager
            page={children.data.page}
            totalPages={children.data.totalPages}
            totalElements={children.data.totalElements}
            onPage={(page) => {
              params.set('page', String(page))
            }}
          />
        </>
      )}
    </section>
  )
}

/** Enrolls or unenrolls with one tap; a closed year answers 409 SCHOOL_YEAR_CLOSED (OPEN-06). */
/** Compact: the column header names the year, so the label is for screen readers only. */
export function EnrollmentToggle({ child, year, compact = false }: { child: AdminChild; year: SchoolYear; compact?: boolean }) {
  const id = useId()
  const enroll = useEnrollChild()
  const unenroll = useUnenrollChild()
  const invalidate = useInvalidateAll()
  const enrolled = child.enrolledSchoolYearIds.includes(year.id)
  const error = enroll.error ?? unenroll.error

  const toggle = async () => {
    try {
      const variables = { childId: child.id, schoolYearId: year.id }
      await (enrolled ? unenroll.mutateAsync(variables) : enroll.mutateAsync(variables))
      await invalidate()
    } catch {
      // shown below
    }
  }

  return (
    <div className="checkbox my-0">
      <input
        id={id}
        type="checkbox"
        checked={enrolled}
        disabled={year.closed || enroll.isPending || unenroll.isPending}
        onChange={() => void toggle()}
      />
      <label htmlFor={id} className={compact ? 'sr-only' : undefined}>
        {texts.admin.children.enrolledIn(year.label)}
      </label>
      {error && (
        <span className="field__error" role="alert">
          {errorText(error)}
        </span>
      )}
    </div>
  )
}
