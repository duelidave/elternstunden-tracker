import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { errorText } from '../api/errors'
import { useCreateChild, useGetChild, useUpdateChild } from '../api/generated/endpoints/children/children'
import { useListSchoolYears } from '../api/generated/endpoints/school-years/school-years'
import type { AdminChild } from '../api/generated/model'
import { CreateChildBody } from '../api/generated/zod/children/children.zod'
import { DateField } from '../components/DateField'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { todayInBerlin } from '../time'
import { EnrollmentToggle } from './ChildrenPage'
import { FamilySelect } from './FamilySelect'
import { useInvalidateAll } from './invalidate'

// Same rules as the server (UI-08); the birth date must not be in the future
const schema = CreateChildBody.extend({
  externalRef: z.string().max(100),
  familyId: z.string(),
  newFamilyLabel: z.string().max(80),
}).refine((child) => child.birthDate <= todayInBerlin(), { path: ['birthDate'] })
type ChildForm = z.input<typeof schema>

/** Create (/admin/children/new) or edit (/admin/children/:childId) a child (7.7). */
export function ChildFormPage() {
  const { childId } = useParams()
  const child = useGetChild(childId ?? '', { query: { enabled: childId !== undefined } })
  if (childId === undefined) {
    return <ChildForm />
  }
  if (child.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (child.isError) {
    return (
      <p className="alert" role="alert">
        {errorText(child.error)}
      </p>
    )
  }
  return <ChildForm child={child.data} />
}

function ChildForm({ child }: { child?: AdminChild }) {
  const navigate = useNavigate()
  const create = useCreateChild()
  const update = useUpdateChild()
  const invalidate = useInvalidateAll()
  const schoolYears = useListSchoolYears()
  const form = useForm<ChildForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      firstName: child?.firstName ?? '',
      lastName: child?.lastName ?? '',
      birthDate: child?.birthDate ?? '',
      externalRef: child?.externalRef ?? '',
      familyId: child?.familyId ?? '',
      newFamilyLabel: '',
    },
  })
  const errors = form.formState.errors
  const familyId = useWatch({ control: form.control, name: 'familyId' })
  const error = create.error ?? update.error

  const submit = form.handleSubmit(async (values) => {
    try {
      if (child === undefined) {
        await create.mutateAsync({
          data: {
            firstName: values.firstName,
            lastName: values.lastName,
            birthDate: values.birthDate,
            externalRef: values.externalRef || undefined,
            familyId: values.familyId || undefined,
            newFamilyLabel: values.newFamilyLabel || undefined,
          },
        })
      } else {
        await update.mutateAsync({
          childId: child.id,
          data: {
            firstName: values.firstName,
            lastName: values.lastName,
            birthDate: values.birthDate,
            externalRef: values.externalRef,
            familyId: values.familyId || undefined,
          },
        })
      }
      await invalidate()
      await navigate('/admin/children')
    } catch {
      // shown below
    }
  })

  return (
    <section className="card">
      <h2>{child === undefined ? texts.admin.children.createTitle : texts.admin.children.editTitle}</h2>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField
          label={texts.admin.children.firstName}
          autoComplete="off"
          error={errors.firstName ? texts.fields.required : undefined}
          {...form.register('firstName')}
        />
        <TextField
          label={texts.admin.children.lastName}
          autoComplete="off"
          error={errors.lastName ? texts.fields.required : undefined}
          {...form.register('lastName')}
        />
        <Controller
          control={form.control}
          name="birthDate"
          render={({ field }) => (
            <DateField
              label={texts.admin.children.birthDate}
              max={todayInBerlin()}
              error={errors.birthDate ? texts.fields.birthDate : undefined}
              {...field}
            />
          )}
        />
        <TextField label={texts.admin.children.externalRef} autoComplete="off" {...form.register('externalRef')} />
        <FamilySelect label={texts.admin.children.family} allowNew={child === undefined} {...form.register('familyId')} />
        {child === undefined && familyId === '' && (
          <TextField
            label={texts.admin.children.newFamilyLabel}
            autoComplete="off"
            {...form.register('newFamilyLabel')}
          />
        )}
        {error && (
          <p className="alert" role="alert">
            {errorText(error)}
          </p>
        )}
        <div className="actions">
          <button type="submit" className="button" disabled={create.isPending || update.isPending}>
            {texts.admin.save}
          </button>
          <Link className="button button--secondary" to="/admin/children">
            {texts.admin.cancel}
          </Link>
        </div>
      </form>
      {child !== undefined && (schoolYears.data?.length ?? 0) > 0 && (
        <div className="mt-6">
          <h3>{texts.admin.children.enrollments}</h3>
          {schoolYears.data?.map((year) => (
            <EnrollmentToggle key={year.id} child={child} year={year} />
          ))}
        </div>
      )}
    </section>
  )
}
