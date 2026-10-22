import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router'
import type { z } from 'zod'
import { errorText } from '../api/errors'
import { useGetFamily, useListFamilies, useUpdateFamily } from '../api/generated/endpoints/families/families'
import type { FamilyDetail, FamilySummary } from '../api/generated/model'
import { UpdateFamilyBody } from '../api/generated/zod/families/families.zod'
import { type Column, DataTable } from '../components/DataTable'
import { Pager } from '../components/Pager'
import { SearchField } from '../components/SearchField'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { formatDate } from '../time'
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
  return <Family family={family.data} />
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
