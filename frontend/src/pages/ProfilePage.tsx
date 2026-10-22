import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import type { z } from 'zod'
import { errorText } from '../api/errors'
import { useLogout } from '../api/generated/endpoints/auth/auth'
import {
  useDeleteIdentity,
  useDisableTotp,
  useStartIdentityLink,
  useUpdateMe,
} from '../api/generated/endpoints/me/me'
import type { Me } from '../api/generated/model'
import { UpdateMeBody } from '../api/generated/zod/me/me.zod'
import { useRefreshSession, useSession } from '../api/session'
import { GoogleLogo } from '../components/GoogleButton'
import { TextField } from '../components/TextField'
import { TotpSetup } from '../components/TotpSetup'
import { texts } from '../i18n/de'

const GOOGLE = { id: 'google', name: 'Google' } as const

/** Profile (13.1): display name, e-mail, linked providers, admin TOTP, logout. */
export function ProfilePage() {
  const session = useSession()
  if (session.isLoading) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (session.me === undefined) {
    return <Navigate to="/login" replace />
  }
  return <Profile me={session.me} />
}

function Profile({ me }: { me: Me }) {
  const refreshSession = useRefreshSession()
  const navigate = useNavigate()
  const logout = useLogout()

  const signOut = async () => {
    try {
      await logout.mutateAsync()
    } catch {
      // the session is gone either way
    }
    await refreshSession()
    await navigate('/login', { replace: true })
  }

  return (
    <>
      <section className="card">
        <h2>{texts.profile.title}</h2>
        <NameForm me={me} />
        <h3>{texts.profile.email}</h3>
        <p className="mb-0 break-all">{me.email}</p>
        <p className="mt-1 text-sm text-muted">{texts.profile.emailHint}</p>
        <button type="button" className="button button--secondary mt-4" onClick={() => void signOut()}>
          {texts.common.logout}
        </button>
      </section>
      {/* INV-12: the master signs in with password and TOTP only */}
      {me.role !== 'MASTER_ADMIN' && <Providers me={me} />}
      {me.role === 'ADMIN' && <SecondFactor me={me} />}
    </>
  )
}

type NameForm = z.input<typeof UpdateMeBody>

function NameForm({ me }: { me: Me }) {
  const update = useUpdateMe()
  const refreshSession = useRefreshSession()
  const form = useForm<NameForm>({ resolver: zodResolver(UpdateMeBody), defaultValues: { displayName: me.displayName } })
  const submit = form.handleSubmit(async (values) => {
    try {
      await update.mutateAsync({ data: values })
      await refreshSession()
    } catch {
      // shown below
    }
  })
  return (
    <form onSubmit={(event) => void submit(event)} noValidate>
      <TextField
        label={texts.profile.name}
        autoComplete="name"
        error={form.formState.errors.displayName ? texts.fields.required : undefined}
        {...form.register('displayName')}
      />
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
      {me.role !== 'MASTER_ADMIN' && (
        <button type="submit" className="button" disabled={update.isPending}>
          {texts.admin.save}
        </button>
      )}
    </form>
  )
}

function Providers({ me }: { me: Me }) {
  const [params] = useSearchParams()
  const start = useStartIdentityLink()
  const remove = useDeleteIdentity()
  const refreshSession = useRefreshSession()
  const identity = me.identities?.find((entry) => entry.provider === GOOGLE.id)
  const linkError = params.get('error')
  const error = start.error ?? remove.error

  const link = async () => {
    try {
      const { redirectUrl } = await start.mutateAsync({ provider: GOOGLE.id })
      // Top-level navigation to the provider (SEC-10: no form post)
      window.location.assign(redirectUrl)
    } catch {
      // shown below
    }
  }

  const unlink = async (identityId: string) => {
    try {
      await remove.mutateAsync({ identityId })
      await refreshSession()
    } catch {
      // shown below
    }
  }

  return (
    <section className="card">
      <h2>{texts.profile.providersTitle}</h2>
      {params.get('linked') !== null && (
        <p className="notice" role="status">
          {texts.profile.linked}
        </p>
      )}
      {linkError !== null && (
        <p className="alert" role="alert">
          {linkError in texts.login.errors
            ? texts.login.errors[linkError as keyof typeof texts.login.errors]
            : texts.login.errors.OIDC_FAILED}
        </p>
      )}
      {identity === undefined ? (
        <button type="button" className="google-button w-full" disabled={start.isPending} onClick={() => void link()}>
          <GoogleLogo />
          <span>{texts.profile.linkProvider(GOOGLE.name)}</span>
        </button>
      ) : (
        <div className="panel">
          <p className="m-0 font-semibold">{texts.profile.providerLinked(GOOGLE.name)}</p>
          {identity.emailAtLink !== undefined && (
            <p className="m-0 text-muted break-all">{texts.profile.providerSince(identity.emailAtLink)}</p>
          )}
          <button
            type="button"
            className="button button--secondary mt-3"
            disabled={remove.isPending}
            onClick={() => void unlink(identity.id)}
          >
            {texts.profile.unlink}
          </button>
        </div>
      )}
      <p className="text-sm text-muted">{texts.profile.emailLinkStays}</p>
      {error && (
        <p className="alert" role="alert">
          {errorText(error)}
        </p>
      )}
    </section>
  )
}

function SecondFactor({ me }: { me: Me }) {
  const disable = useDisableTotp()
  const refreshSession = useRefreshSession()

  const switchOff = async () => {
    if (!window.confirm(texts.totp.confirmDisable)) {
      return
    }
    try {
      await disable.mutateAsync()
      await refreshSession()
    } catch {
      // shown below
    }
  }

  return (
    <section className="card">
      <h2>{texts.totp.title}</h2>
      {me.totpEnabled === true ? (
        <>
          <p>{texts.totp.enabled}</p>
          {me.totpRequired === true ? (
            <p className="text-muted">{texts.totp.required}</p>
          ) : (
            <button
              type="button"
              className="button button--secondary"
              disabled={disable.isPending}
              onClick={() => void switchOff()}
            >
              {texts.totp.disable}
            </button>
          )}
          {disable.isError && (
            <p className="alert" role="alert">
              {errorText(disable.error)}
            </p>
          )}
        </>
      ) : (
        <>
          <p>{texts.totp.disabled}</p>
          <TotpSetup onDone={() => void refreshSession()} />
        </>
      )}
    </section>
  )
}
