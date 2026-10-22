import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate } from 'react-router'
import { z } from 'zod'
import { errorText } from '../api/errors'
import { useUpdateMyClaim } from '../api/generated/endpoints/me/me'
import type { ChildClaim } from '../api/generated/model'
import { UpdateMyClaimBody } from '../api/generated/zod/me/me.zod'
import { useRefreshSession, useSession } from '../api/session'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { formatDate, todayInBerlin } from '../time'

// Same rules as the server (UI-08, 7.1.2)
const schema = UpdateMyClaimBody.refine((child) => child.birthDate <= todayInBerlin(), { path: ['birthDate'] })
type ClaimForm = z.input<typeof schema>

/** Waiting screen for family members without family (7.1.7): own child data and up to 3 corrections. */
export function PendingPage() {
  const session = useSession()

  if (session.isLoading) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (session.me === undefined) {
    return <Navigate to="/login" replace />
  }
  if (session.me.role !== 'FAMILY_MEMBER' || session.me.assignedToFamily) {
    return <Navigate to="/" replace />
  }
  return <Pending claim={session.me.pendingClaim} correctionsLeft={session.me.claimCorrectionsLeft ?? 0} />
}

function Pending({ claim, correctionsLeft }: { claim: ChildClaim | undefined; correctionsLeft: number }) {
  const [saved, setSaved] = useState(false)
  const update = useUpdateMyClaim()
  const refreshSession = useRefreshSession()
  const canEdit = claim === undefined || correctionsLeft > 0
  const form = useForm<ClaimForm>({
    resolver: zodResolver(schema),
    defaultValues: claim ?? { firstName: '', lastName: '', birthDate: '' },
  })
  const errors = form.formState.errors

  const submit = form.handleSubmit(async (values) => {
    try {
      await update.mutateAsync({ data: values })
      setSaved(true)
      await refreshSession()
    } catch {
      // shown via update.error
    }
  })

  return (
    <section className="card">
      <h2>{texts.pending.title}</h2>
      <p>{texts.pending.intro}</p>
      {claim !== undefined && (
        <div className="panel">
          <h3>{texts.pending.yourData}</h3>
          <p className="m-0">
            {texts.pending.child(`${claim.firstName} ${claim.lastName}`, formatDate(claim.birthDate))}
          </p>
        </div>
      )}
      {saved && (
        <p className="notice" role="status">
          {texts.pending.saved}
        </p>
      )}
      {canEdit ? (
        <form onSubmit={(event) => void submit(event)} noValidate>
          <h3>{claim === undefined ? texts.pending.enterTitle : texts.pending.correctTitle}</h3>
          <p className="text-muted">
            {claim === undefined ? texts.pending.enterIntro : texts.pending.correctionsLeft(correctionsLeft)}
          </p>
          <TextField
            label={texts.register.childFirstName}
            autoComplete="off"
            error={errors.firstName ? texts.fields.required : undefined}
            {...form.register('firstName')}
          />
          <TextField
            label={texts.register.childLastName}
            autoComplete="off"
            error={errors.lastName ? texts.fields.required : undefined}
            {...form.register('lastName')}
          />
          <TextField
            label={texts.register.childBirthDate}
            type="date"
            max={todayInBerlin()}
            error={errors.birthDate ? texts.fields.birthDate : undefined}
            {...form.register('birthDate')}
          />
          {update.isError && (
            <p className="alert" role="alert">
              {errorText(update.error)}
            </p>
          )}
          <button type="submit" className="button w-full" disabled={update.isPending}>
            {texts.pending.submit}
          </button>
        </form>
      ) : (
        <p>{texts.pending.noCorrections}</p>
      )}
    </section>
  )
}
