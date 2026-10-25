import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { errorText } from '../api/errors'
import {
  useCompleteOidcRegistration,
  useGetOidcPending,
  useListLoginProviders,
} from '../api/generated/endpoints/auth/auth'
import type { OidcPending } from '../api/generated/model'
import { CompleteOidcRegistrationBody } from '../api/generated/zod/auth/auth.zod'
import { useSessionChanged } from '../api/session'
import { DateField } from '../components/DateField'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { todayInBerlin } from '../time'
import { nextPath } from './loginResult'

// Same rules as the server (UI-08, 7.1.2)
const schema = CompleteOidcRegistrationBody.extend({
  child: CompleteOidcRegistrationBody.shape.child.refine((child) => child.birthDate <= todayInBerlin(), {
    path: ['birthDate'],
  }),
})
type ClaimForm = z.input<typeof schema>

/** "Noch ein Schritt" after the first Google login (7.1.5 step 4): name and child data. */
export function OidcClaimPage() {
  const pending = useGetOidcPending({ query: { retry: false } })
  if (pending.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (pending.isError) {
    return (
      <section className="card">
        <h2>{texts.oidcClaim.title}</h2>
        <p>{texts.oidcClaim.expired}</p>
        <Link className="button button--secondary" to="/login">
          {texts.common.backToLogin}
        </Link>
      </section>
    )
  }
  return <ClaimFormView pending={pending.data} />
}

function ClaimFormView({ pending }: { pending: OidcPending }) {
  const complete = useCompleteOidcRegistration()
  const refreshSession = useSessionChanged()
  const navigate = useNavigate()
  const form = useForm<ClaimForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: pending.displayName,
      child: { firstName: '', lastName: '', birthDate: '' },
    },
  })
  const providers = useListLoginProviders({ query: { staleTime: Infinity } })
  const providerName = (id: string) => providers.data?.find((provider) => provider.id === id)?.name ?? id
  const errors = form.formState.errors

  const submit = form.handleSubmit(async (values) => {
    try {
      const result = await complete.mutateAsync({ data: values })
      await refreshSession()
      await navigate(nextPath(result), { replace: true })
    } catch {
      // shown below
    }
  })

  return (
    <section className="card">
      <h2>{texts.oidcClaim.title}</h2>
      <p className="text-muted">{texts.oidcClaim.account(providerName(pending.provider), pending.email)}</p>
      <p>{texts.oidcClaim.intro}</p>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField
          label={texts.register.displayName}
          autoComplete="name"
          error={errors.displayName ? texts.fields.required : undefined}
          {...form.register('displayName')}
        />
        <fieldset>
          <legend>{texts.register.childHeading}</legend>
          <TextField
            label={texts.register.childFirstName}
            autoComplete="off"
            error={errors.child?.firstName ? texts.fields.required : undefined}
            {...form.register('child.firstName')}
          />
          <TextField
            label={texts.register.childLastName}
            autoComplete="off"
            error={errors.child?.lastName ? texts.fields.required : undefined}
            {...form.register('child.lastName')}
          />
          <Controller
            control={form.control}
            name="child.birthDate"
            render={({ field }) => (
              <DateField
                label={texts.register.childBirthDate}
                max={todayInBerlin()}
                error={errors.child?.birthDate ? texts.fields.birthDate : undefined}
                {...field}
              />
            )}
          />
        </fieldset>
        {complete.isError && (
          <p className="alert" role="alert">
            {errorText(complete.error)}
          </p>
        )}
        <button type="submit" className="button w-full" disabled={complete.isPending}>
          {texts.oidcClaim.submit}
        </button>
      </form>
    </section>
  )
}
