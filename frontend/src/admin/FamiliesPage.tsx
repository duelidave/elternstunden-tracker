import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router'
import type { z } from 'zod'
import { errorText } from '../api/errors'
import { useCreateEntryForFamily, useListEntries } from '../api/generated/endpoints/entries/entries'
import { useGetFamily, useListFamilies, useUpdateFamily } from '../api/generated/endpoints/families/families'
import type { FamilyDetail, FamilySummary } from '../api/generated/model'
import { UpdateFamilyBody } from '../api/generated/zod/families/families.zod'
import { type Column, DataTable } from '../components/DataTable'
import { EntryForm, type EntryValues } from '../components/EntryForm'
import { Pager } from '../components/Pager'
import { SearchField } from '../components/SearchField'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { formatDate } from '../time'
import { AdminEntryItem } from './EntriesPage'
import { useInvalidateAll } from './invalidate'
import { useListParams } from './params'

const columns: Column<FamilySummary>[] = [
  {
    header: texts.admin.families.family,
    cell: (family) => (
      <Link to={`/admin/families/${family.id}`}>{family.displayLabel || texts.admin.families.noLabel}</Link>
    ),
  },
  {
    header: texts.admin.families.children,
    cell: (family) => family.children.map((child) => `${child.firstName} ${child.lastName}`).join(', ') || texts.admin.none,
  },
  { header: texts.admin.families.members, cell: (family) => String(family.memberCount) },
]

/** Families (FS-ADM 7.7); the overview with hours follows in M5 (FS-ADMOV). */
export function FamiliesPage() {
  const params = useListParams()
  const families = useListFamilies({ q: params.get('q') || undefined, page: params.page })
  return (
    <section className="card">
      <h2>{texts.admin.families.title}</h2>
      <SearchField
        value={params.get('q')}
        onSearch={(query) => {
          params.set('q', query)
        }}
      />
      {families.isError && (
        <p className="alert" role="alert">
          {errorText(families.error)}
        </p>
      )}
      {families.data && (
        <>
          <DataTable
            caption={texts.admin.families.title}
            columns={columns}
            rows={families.data.content}
            rowKey={(family) => family.id}
            empty={texts.admin.empty}
          />
          <Pager
            page={families.data.page}
            totalPages={families.data.totalPages}
            totalElements={families.data.totalElements}
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
