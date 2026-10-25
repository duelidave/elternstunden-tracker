import { zodResolver } from '@hookform/resolvers/zod'
import {
  browserSupportsWebAuthn,
  startRegistration,
  type PublicKeyCredentialCreationOptionsJSON,
} from '@simplewebauthn/browser'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import type { z } from 'zod'
import { errorText, textFor } from '../api/errors'
import { useListLoginProviders, useLogout } from '../api/generated/endpoints/auth/auth'
import {
  getListPasskeysQueryKey,
  useDeleteIdentity,
  useDeletePasskey,
  useDisableTotp,
  useListPasskeys,
  useRegisterPasskey,
  useStartIdentityLink,
  useStartPasskeyRegistration,
  useUpdateMe,
} from '../api/generated/endpoints/me/me'
import type { Me, WebAuthnJson } from '../api/generated/model'
import { ApiProblem } from '../api/http'
import { UpdateMeBody } from '../api/generated/zod/me/me.zod'
import { useRefreshSession, useSession, useSessionChanged } from '../api/session'
import { GoogleLogo } from '../components/GoogleButton'
import { NetIdLogo } from '../components/NetIdButton'
import { KeyIcon } from '../components/PasskeyLogin'
import { TextField } from '../components/TextField'
import { TotpSetup } from '../components/TotpSetup'
import { texts } from '../i18n/de'
import { formatDateTime } from '../time'

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
  const sessionChanged = useSessionChanged()
  const navigate = useNavigate()
  const logout = useLogout()

  const signOut = async () => {
    try {
      await logout.mutateAsync()
    } catch {
      // the session is gone either way
    }
    await sessionChanged()
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
      {me.role !== 'MASTER_ADMIN' && <Passkeys />}
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

/** Linked providers (7.2 "Profil"): one entry per active provider, linking needs a fresh login. */
function Providers({ me }: { me: Me }) {
  const [params] = useSearchParams()
  const providers = useListLoginProviders({ query: { staleTime: Infinity } })
  const start = useStartIdentityLink()
  const remove = useDeleteIdentity()
  const refreshSession = useRefreshSession()
  const linkError = params.get('error')
  const error = start.error ?? remove.error

  const link = async (provider: string) => {
    try {
      const { redirectUrl } = await start.mutateAsync({ provider })
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
          {textFor(texts.login.errors, linkError, texts.login.errors.OIDC_FAILED)}
        </p>
      )}
      <ul className="stack">
        {(providers.data ?? []).map((provider) => {
          const identity = me.identities?.find((entry) => entry.provider === provider.id)
          return (
            <li key={provider.id}>
              {identity === undefined ? (
                <button
                  type="button"
                  className={provider.id === 'google' ? 'google-button w-full' : 'button button--secondary w-full'}
                  disabled={start.isPending}
                  onClick={() => void link(provider.id)}
                >
                  {provider.id === 'google' && <GoogleLogo />}
                  {provider.id === 'netid' && <NetIdLogo />}
                  <span>{texts.profile.linkProvider(provider.name)}</span>
                </button>
              ) : (
                <div className="panel">
                  <p className="m-0 font-semibold">{texts.profile.providerLinked(provider.name)}</p>
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
            </li>
          )
        })}
      </ul>
      <p className="text-sm text-muted">{texts.profile.emailLinkStays}</p>
      {error && (
        <p className="alert" role="alert">
          {errorText(error)}
        </p>
      )}
    </section>
  )
}

/** Passkeys (7.2, D-30): create with a fresh login, list, remove. Not for the master (INV-12). */
function Passkeys() {
  const passkeys = useListPasskeys()
  const start = useStartPasskeyRegistration()
  const register = useRegisterPasskey()
  const remove = useDeletePasskey()
  const client = useQueryClient()
  const [label, setLabel] = useState('')
  const [message, setMessage] = useState<string | undefined>()
  const [error, setError] = useState<string | undefined>()

  if (!browserSupportsWebAuthn()) {
    return (
      <section className="card">
        <h2>{texts.passkey.title}</h2>
        <p className="text-muted">{texts.passkey.unsupported}</p>
      </section>
    )
  }

  const reload = async () => {
    await client.invalidateQueries({ queryKey: getListPasskeysQueryKey() })
  }

  const add = async () => {
    setMessage(undefined)
    setError(undefined)
    try {
      const { challengeId, options } = await start.mutateAsync()
      const credential = await startRegistration({
        optionsJSON: options as unknown as PublicKeyCredentialCreationOptionsJSON,
      })
      await register.mutateAsync({
        data: {
          challengeId,
          credential: credential as unknown as WebAuthnJson,
          ...(label.trim() === '' ? {} : { label: label.trim() }),
        },
      })
      setLabel('')
      setMessage(texts.passkey.added)
      await reload()
    } catch (failure) {
      // Cancelled in the browser's dialog: nothing to say
      if (failure instanceof Error && failure.name === 'NotAllowedError') {
        return
      }
      setError(failure instanceof ApiProblem ? errorText(failure) : texts.errors.PASSKEY_INVALID)
    }
  }

  const removePasskey = async (id: string) => {
    if (!window.confirm(texts.passkey.confirmRemove)) {
      return
    }
    setMessage(undefined)
    setError(undefined)
    try {
      await remove.mutateAsync({ passkeyId: id })
      setMessage(texts.passkey.removed)
      await reload()
    } catch (failure) {
      setError(errorText(failure))
    }
  }

  return (
    <section className="card">
      <h2>{texts.passkey.title}</h2>
      <p>{texts.passkey.intro}</p>
      <p aria-live="polite" className="m-0">
        {message !== undefined && <span className="notice block">{message}</span>}
      </p>
      {error !== undefined && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      {passkeys.data?.length === 0 && <p className="text-muted">{texts.passkey.none}</p>}
      <ul className="stack">
        {passkeys.data?.map((passkey) => (
          <li key={passkey.id} className="panel">
            <p className="m-0 font-semibold">{passkey.label}</p>
            <p className="m-0 text-sm text-muted">
              {texts.passkey.created(formatDateTime(passkey.createdAt))}
              {passkey.lastUsedAt !== undefined && ` · ${texts.passkey.lastUsed(formatDateTime(passkey.lastUsedAt))}`}
            </p>
            <button
              type="button"
              className="button button--secondary mt-3"
              disabled={remove.isPending}
              onClick={() => void removePasskey(passkey.id)}
            >
              {texts.passkey.remove}
            </button>
          </li>
        ))}
      </ul>
      <TextField
        label={texts.passkey.label}
        placeholder={texts.passkey.labelPlaceholder}
        maxLength={60}
        autoComplete="off"
        value={label}
        onChange={(event) => {
          setLabel(event.target.value)
        }}
      />
      <button
        type="button"
        className="button"
        disabled={start.isPending || register.isPending}
        onClick={() => void add()}
      >
        <KeyIcon />
        <span>{texts.passkey.add}</span>
      </button>
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
