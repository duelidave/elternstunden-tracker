import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router'
import type { z } from 'zod'
import { errorText, hasCode } from '../api/errors'
import { useCreateEntryForFamily, useListEntries } from '../api/generated/endpoints/entries/entries'
import { useGetFamily, useUpdateFamily } from '../api/generated/endpoints/families/families'
import { useGetAdminOverview } from '../api/generated/endpoints/overview/overview'
import { useListSchoolYears } from '../api/generated/endpoints/school-years/school-years'
import type { FamilyDetail, OverviewRow } from '../api/generated/model'
import { UpdateFamilyBody } from '../api/generated/zod/families/families.zod'
import { type Column, DataTable, type Sort } from '../components/DataTable'
import { EntryForm, type EntryValues } from '../components/EntryForm'
import { Pager } from '../components/Pager'
import { SearchField } from '../components/SearchField'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { formatDate, formatDuration } from '../time'
import { AdminEntryItem } from './EntriesPage'
import { useInvalidateAll } from './invalidate'
import { useListParams } from './params'

const columns: Column<OverviewRow>[] = [
  {
    header: texts.admin.families.family,
    sortField: 'familyLabel',
    cell: (row) => (
      <Link to={`/admin/families/${row.familyId}`}>{row.familyLabel || texts.admin.families.noLabel}</Link>
    ),
  },
  {
    header: texts.admin.families.children,
    sortField: 'children',
    cell: (row) => row.children.map((child) => `${child.firstName} ${child.lastName}`).join(', ') || texts.admin.none,
  },
  { header: texts.admin.families.members, sortField: 'memberCount', cell: (row) => String(row.memberCount) },
  {
    header: texts.admin.overview.confirmed,
    sortField: 'confirmedMinutes',
    cell: (row) => formatDuration(row.confirmedMinutes),
  },
  { header: texts.admin.overview.open, sortField: 'openMinutes', cell: (row) => formatDuration(row.openMinutes) },
  {
    header: texts.admin.overview.missing,
    sortField: 'missingMinutes',
    cell: (row) => (
      <span>
        {row.missingMinutes === 0 ? (
          <span className="status status--confirmed">{texts.admin.overview.fulfilled}</span>
        ) : (
          <span className="status">{texts.admin.overview.missingChip(formatDuration(row.missingMinutes))}</span>
        )}
        {row.openMinutes > 0 && (
          <span className="block text-sm text-muted">
            {texts.admin.overview.waiting(formatDuration(row.openMinutes))}
          </span>
        )}
      </span>
    ),
  },
]

// The direction a column starts with: names A to Z, numbers largest first
const firstDirection: Record<string, Sort['direction']> = {
  familyLabel: 'asc',
  children: 'asc',
  memberCount: 'desc',
  confirmedMinutes: 'desc',
  openMinutes: 'desc',
  missingMinutes: 'desc',
}
const DEFAULT_SORT = 'missingMinutes,desc'

function parseSort(value: string): Sort {
  const [field = 'missingMinutes', direction] = value.split(',')
  return { field, direction: direction === 'asc' ? 'asc' : 'desc' }
}

/**
 * Families with their hours in a school year (FS-ADMOV 7.6, D-11): search, only missing ones, sortable columns and
 * paging, everything in the URL (UI-11). A click on a family opens its detail.
 */
export function FamiliesPage() {
  const params = useListParams()
  const yearId = useId()
  const missingId = useId()
  const sortId = useId()
  const schoolYears = useListSchoolYears()
  const sortValue = params.get('sort') || DEFAULT_SORT
  const sort = parseSort(sortValue)
  const overview = useGetAdminOverview(
    {
      ...(params.get('year') === '' ? {} : { schoolYearId: params.get('year') }),
      ...(params.get('q') === '' ? {} : { q: params.get('q') }),
      onlyMissing: params.get('onlyMissing') === 'true',
      sort: sortValue,
      page: params.page,
    },
    // The table stays while the next sort or page loads
    { query: { placeholderData: keepPreviousData } },
  )

  const sortBy = (field: string) => {
    const direction =
      field === sort.field ? (sort.direction === 'asc' ? 'desc' : 'asc') : (firstDirection[field] ?? 'asc')
    const next = `${field},${direction}`
    params.set('sort', next === DEFAULT_SORT ? '' : next)
  }

  return (
    <section className="card">
      <h2>{texts.admin.families.title}</h2>
      <div className="overview-filters">
        <SearchField
          value={params.get('q')}
          onSearch={(query) => {
            params.set('q', query)
          }}
        />
        <div className="field">
          <label htmlFor={yearId}>{texts.admin.overview.schoolYear}</label>
          <select
            id={yearId}
            value={overview.data?.schoolYear.id ?? params.get('year')}
            onChange={(event) => {
              const current = schoolYears.data?.find((year) => year.current)?.id
              params.set('year', event.target.value === current ? '' : event.target.value)
            }}
          >
            {schoolYears.data?.map((year) => (
              <option key={year.id} value={year.id}>
                {year.label}
              </option>
            ))}
          </select>
        </div>
        <div className="checkbox">
          <input
            id={missingId}
            type="checkbox"
            checked={params.get('onlyMissing') === 'true'}
            onChange={(event) => {
              params.set('onlyMissing', event.target.checked ? 'true' : '')
            }}
          />
          <label htmlFor={missingId}>{texts.admin.overview.onlyMissing}</label>
        </div>
      </div>
      {/* Below 768 px the rows are cards without column heads, so the sort is a select */}
      <div className="field md:hidden">
        <label htmlFor={sortId}>{texts.admin.sort}</label>
        <select
          id={sortId}
          value={sortValue}
          onChange={(event) => {
            params.set('sort', event.target.value === DEFAULT_SORT ? '' : event.target.value)
          }}
        >
          {columns.map((column) => {
            const value = `${column.sortField ?? ''},${firstDirection[column.sortField ?? ''] ?? 'asc'}`
            return (
              <option key={value} value={value}>
                {column.header}
              </option>
            )
          })}
          {/* A sort chosen in the table on a wide screen, e.g. the other direction */}
          {!columns.some((column) => `${column.sortField ?? ''},${firstDirection[column.sortField ?? ''] ?? ''}` === sortValue) && (
            <option value={sortValue}>
              {columns.find((column) => column.sortField === sort.field)?.header}{' '}
              {sort.direction === 'asc' ? '▲' : '▼'}
            </option>
          )}
        </select>
      </div>
      {overview.isError && (
        <p className={hasCode(overview.error, 'NO_CURRENT_SCHOOL_YEAR') ? 'text-muted' : 'alert'} role="alert">
          {hasCode(overview.error, 'NO_CURRENT_SCHOOL_YEAR') ? texts.admin.overview.noYear : errorText(overview.error)}
        </p>
      )}
      {overview.isPending && <p role="status">{texts.common.loading}</p>}
      {overview.data && (
        <>
          <DataTable
            caption={texts.admin.families.title}
            columns={columns}
            rows={overview.data.content}
            rowKey={(row) => row.familyId}
            empty={texts.admin.overview.empty}
            sort={sort}
            onSort={sortBy}
          />
          <Pager
            page={overview.data.page}
            totalPages={overview.data.totalPages}
            totalElements={overview.data.totalElements}
            onPage={(page) => {
              params.set('page', String(page))
            }}
          />
        </>
      )}
    </section>
  )
}

/** One family: label and note, children, members (7.7). */
export function FamilyPage() {
  const { familyId = '' } = useParams()
  const family = useGetFamily(familyId)
  if (family.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (family.isError) {
    return (
      <p className="alert" role="alert">
        {errorText(family.error)}
      </p>
    )
  }
  return (
    <>
      <Family family={family.data} />
      <FamilyEntries familyId={family.data.id} />
    </>
  )
}

/** Entries of one family for corrections, and entering hours for it (INV-02: admins may enter for every family). */
function FamilyEntries({ familyId }: { familyId: string }) {
  const entries = useListEntries({ familyId, size: 200 })
  const create = useCreateEntryForFamily()
  const invalidate = useInvalidateAll()
  const [adding, setAdding] = useState(false)
  const [message, setMessage] = useState<string | undefined>()

  const add = async (values: EntryValues) => {
    try {
      await create.mutateAsync({ familyId, data: values })
      setMessage(texts.admin.entries.added)
      setAdding(false)
    } catch {
      // shown below
    }
    await invalidate()
  }

  // Newest first, like the family sees them
  const list = [...(entries.data?.content ?? [])].sort((a, b) =>
    a.workDate === b.workDate ? b.createdAt.localeCompare(a.createdAt) : b.workDate.localeCompare(a.workDate),
  )
  return (
    <section className="card">
      <div className="card__header">
        <h2>{texts.admin.entries.familyEntries}</h2>
        {!adding && (
          <button
            type="button"
            className="button"
            onClick={() => {
              setAdding(true)
            }}
          >
            {texts.admin.entries.add}
          </button>
        )}
      </div>
      <p aria-live="polite" className="m-0">
        {message !== undefined && <span className="notice mt-3 block">{message}</span>}
      </p>
      {adding && (
        <div className="panel mt-3">
          {create.isError && (
            <p className="alert" role="alert">
              {errorText(create.error)}
            </p>
          )}
          <EntryForm minDate="2000-01-01" submitLabel={texts.entry.submit} onSubmit={add} />
          <button
            type="button"
            className="button button--secondary mt-3 w-full"
            onClick={() => {
              setAdding(false)
            }}
          >
            {texts.admin.cancel}
          </button>
        </div>
      )}
      {entries.isError && (
        <p className="alert" role="alert">
          {errorText(entries.error)}
        </p>
      )}
      {entries.data && list.length === 0 && <p className="text-muted">{texts.admin.entries.noEntries}</p>}
      <ul className="stack mt-4">
        {list.map((entry) => (
          <AdminEntryItem key={entry.id} entry={entry} onMessage={setMessage} />
        ))}
      </ul>
    </section>
  )
}

type FamilyForm = z.input<typeof UpdateFamilyBody>

function Family({ family }: { family: FamilyDetail }) {
  const update = useUpdateFamily()
  const invalidate = useInvalidateAll()
  const form = useForm<FamilyForm>({
    resolver: zodResolver(UpdateFamilyBody),
    defaultValues: { label: family.label ?? '', note: family.note ?? '' },
  })

  const submit = form.handleSubmit(async (values) => {
    try {
      await update.mutateAsync({ familyId: family.id, data: { label: values.label ?? '', note: values.note ?? '' } })
      await invalidate()
    } catch {
      // shown below
    }
  })

  return (
    <section className="card">
      <h2>{family.displayLabel || texts.admin.families.noLabel}</h2>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField label={texts.admin.families.label} autoComplete="off" {...form.register('label')} />
        <div className="field">
          <label htmlFor="family-note">{texts.admin.families.note}</label>
          <textarea id="family-note" rows={3} {...form.register('note')} />
        </div>
        {update.isError && (
          <p className="alert" role="alert">
            {errorText(update.error)}
          </p>
        )}
        {update.isSuccess && (
          <p className="notice" role="status">
            {texts.admin.saved}
          </p>
        )}
        <button type="submit" className="button" disabled={update.isPending}>
          {texts.admin.save}
        </button>
      </form>
      <h3 className="mt-6">{texts.admin.families.children}</h3>
      {family.children.length === 0 ? (
        <p className="text-muted">{texts.admin.families.noChildren}</p>
      ) : (
        <ul className="stack">
          {family.children.map((child) => (
            <li key={child.id}>
              <Link to={`/admin/children/${child.id}`}>
                {child.firstName} {child.lastName}
              </Link>
              , {formatDate(child.birthDate)}
            </li>
          ))}
        </ul>
      )}
      <h3 className="mt-6">{texts.admin.families.members}</h3>
      {family.members.length === 0 ? (
        <p className="text-muted">{texts.admin.families.noMembers}</p>
      ) : (
        <ul className="stack">
          {family.members.map((member) => (
            <li key={member.id}>
              <Link to={`/admin/users/${member.id}`}>{member.displayName}</Link>{' '}
              <span className="text-muted">({texts.admin.users.statuses[member.status]})</span>
            </li>
          ))}
        </ul>
      )}
      <p className="card__footer">
        <Link to="/admin/families">{texts.admin.back}</Link>
      </p>
    </section>
  )
}
