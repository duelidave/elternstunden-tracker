import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { errorText } from '../api/errors'
import {
  useCloseSchoolYear,
  useCopyPreviousEnrollments,
  useCreateSchoolYear,
  useListSchoolYears,
  useReopenSchoolYear,
  useUpdateSchoolYear,
} from '../api/generated/endpoints/school-years/school-years'
import type { SchoolYear } from '../api/generated/model'
import { CreateSchoolYearBody } from '../api/generated/zod/school-years/school-years.zod'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { formatDate, minutesToHours } from '../time'
import { useInvalidateAll } from './invalidate'

// The required time is entered in hours (15-minute grid) and sent as minutes (8.1)
const schema = CreateSchoolYearBody.omit({ requiredMinutes: true })
  .extend({ requiredHours: z.coerce.number().min(0).max(1000).multipleOf(0.25) })
  .refine((year) => year.endsOn > year.startsOn, { path: ['endsOn'] })
type YearForm = z.input<typeof schema>

/** School years (5.5, FS-ADM 7.7): cut-off dates, required hours, close and reopen (OPEN-06). */
export function SchoolYearsPage() {
  const years = useListSchoolYears()
  const [editing, setEditing] = useState<string | undefined>()
  return (
    <>
      <section className="card">
        <h2>{texts.admin.schoolYears.title}</h2>
        {years.isError && (
          <p className="alert" role="alert">
            {errorText(years.error)}
          </p>
        )}
        {years.data?.length === 0 && <p className="text-muted">{texts.admin.schoolYears.empty}</p>}
        <ul className="stack">
          {years.data?.map((year) =>
            editing === year.id ? (
              <li key={year.id} className="panel">
                <YearForm
                  year={year}
                  onDone={() => {
                    setEditing(undefined)
                  }}
                />
              </li>
            ) : (
              <YearRow
                key={year.id}
                year={year}
                onEdit={() => {
                  setEditing(year.id)
                }}
              />
            ),
          )}
        </ul>
      </section>
      <section className="card">
        <h2>{texts.admin.schoolYears.create}</h2>
        <YearForm />
      </section>
    </>
  )
}

function YearRow({ year, onEdit }: { year: SchoolYear; onEdit: () => void }) {
  const close = useCloseSchoolYear()
  const reopen = useReopenSchoolYear()
  const copy = useCopyPreviousEnrollments()
  const invalidate = useInvalidateAll()
  const busy = close.isPending || reopen.isPending || copy.isPending
  const error = close.error ?? reopen.error ?? copy.error

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      await invalidate()
    } catch {
      // shown below
    }
  }

  return (
    <li className="panel">
      <p className="m-0 font-semibold">
        {year.label}
        {year.current && <span className="chip">{texts.admin.schoolYears.current}</span>}
        <span className="chip chip--muted">
          {year.closed ? texts.admin.schoolYears.closed : texts.admin.schoolYears.open}
        </span>
      </p>
      <p className="m-0 text-muted">
        {formatDate(year.startsOn)} – {formatDate(year.endsOn)} · {minutesToHours(year.requiredMinutes)} h
      </p>
      <div className="actions">
        {!year.closed && (
          <button type="button" className="button button--secondary" disabled={busy} onClick={onEdit}>
            {texts.admin.edit}
          </button>
        )}
        {!year.closed && (
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => void run(() => copy.mutateAsync({ schoolYearId: year.id }))}
          >
            {texts.admin.schoolYears.copyPrevious}
          </button>
        )}
        {year.closed ? (
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => void run(() => reopen.mutateAsync({ schoolYearId: year.id }))}
          >
            {texts.admin.schoolYears.reopen}
          </button>
        ) : (
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => {
              if (window.confirm(texts.admin.schoolYears.confirmClose)) {
                void run(() => close.mutateAsync({ schoolYearId: year.id }))
              }
            }}
          >
            {texts.admin.schoolYears.close}
          </button>
        )}
      </div>
      {copy.isSuccess && (
        <p className="notice" role="status">
          {texts.admin.schoolYears.copied(copy.data.copied)}
        </p>
      )}
      {error && (
        <p className="alert" role="alert">
          {errorText(error)}
        </p>
      )}
    </li>
  )
}

function YearForm({ year, onDone }: { year?: SchoolYear; onDone?: () => void }) {
  const create = useCreateSchoolYear()
  const update = useUpdateSchoolYear()
  const invalidate = useInvalidateAll()
  const form = useForm<YearForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      label: year?.label ?? '',
      startsOn: year?.startsOn ?? '',
      endsOn: year?.endsOn ?? '',
      requiredHours: year === undefined ? 10 : minutesToHours(year.requiredMinutes),
    },
  })
  const errors = form.formState.errors
  const error = create.error ?? update.error

  const submit = form.handleSubmit(async (values) => {
    const data = {
      label: values.label,
      startsOn: values.startsOn,
      endsOn: values.endsOn,
      requiredMinutes: Math.round(Number(values.requiredHours) * 60),
    }
    try {
      if (year === undefined) {
        await create.mutateAsync({ data })
        form.reset()
      } else {
        await update.mutateAsync({ schoolYearId: year.id, data })
      }
      await invalidate()
      onDone?.()
    } catch {
      // shown below
    }
  })

  return (
    <form onSubmit={(event) => void submit(event)} noValidate>
      <TextField
        label={texts.admin.schoolYears.label}
        placeholder="2027/28"
        autoComplete="off"
        error={errors.label ? texts.fields.required : undefined}
        {...form.register('label')}
      />
      <TextField
        label={texts.admin.schoolYears.startsOn}
        type="date"
        error={errors.startsOn ? texts.fields.required : undefined}
        {...form.register('startsOn')}
      />
      <TextField
        label={texts.admin.schoolYears.endsOn}
        type="date"
        error={errors.endsOn ? texts.fields.dateRange : undefined}
        {...form.register('endsOn')}
      />
      <TextField
        label={texts.admin.schoolYears.required}
        type="number"
        inputMode="decimal"
        step="0.25"
        min="0"
        error={errors.requiredHours ? texts.fields.hours : undefined}
        {...form.register('requiredHours')}
      />
      {error && (
        <p className="alert" role="alert">
          {errorText(error)}
        </p>
      )}
      <div className="actions">
        <button type="submit" className="button" disabled={create.isPending || update.isPending}>
          {texts.admin.save}
        </button>
        {onDone && (
          <button type="button" className="button button--secondary" onClick={onDone}>
            {texts.admin.cancel}
          </button>
        )}
      </div>
    </form>
  )
}
