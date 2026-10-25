import { useId, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { errorText } from '../api/errors'
import { useVerifyMfa } from '../api/generated/endpoints/auth/auth'
import { ApiProblem } from '../api/http'
import { useSessionChanged } from '../api/session'
import { TotpSetup } from '../components/TotpSetup'
import { texts } from '../i18n/de'
import { nextPath } from './loginResult'

/** Second factor for admins (7.2): enter the code, or set TOTP up first when it is required. */
export function MfaPage() {
  const [params] = useSearchParams()
  const refreshSession = useSessionChanged()
  const navigate = useNavigate()

  const finish = async (path: string) => {
    await refreshSession()
    await navigate(path, { replace: true })
  }

  if (params.get('setup') === '1') {
    return (
      <section className="card">
        <h2>{texts.mfa.setupTitle}</h2>
        <p>{texts.mfa.setupIntro}</p>
        <TotpSetup onDone={() => void finish('/')} />
      </section>
    )
  }
  return <Verify onDone={(path) => void finish(path)} />
}

function Verify({ onDone }: { onDone: (path: string) => void }) {
  const id = useId()
  const [code, setCode] = useState('')
  const verify = useVerifyMfa()
  const expired = verify.error instanceof ApiProblem && verify.error.status === 401

  if (expired) {
    return (
      <section className="card">
        <h2>{texts.mfa.title}</h2>
        <p>{texts.mfa.expired}</p>
        <Link className="button button--secondary" to="/login">
          {texts.common.backToLogin}
        </Link>
      </section>
    )
  }

  return (
    <section className="card">
      <h2>{texts.mfa.title}</h2>
      <p>{texts.mfa.intro}</p>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          verify.mutate(
            { data: { code: code.trim() } },
            {
              onSuccess: (result) => {
                onDone(nextPath(result))
              },
            },
          )
        }}
        noValidate
      >
        <div className="field">
          <label htmlFor={id}>{texts.mfa.code}</label>
          <input
            id={id}
            autoComplete="one-time-code"
            maxLength={20}
            value={code}
            onChange={(event) => {
              setCode(event.target.value)
            }}
          />
        </div>
        <p className="text-sm text-muted">{texts.mfa.recoveryHint}</p>
        {verify.isError && (
          <p className="alert" role="alert">
            {errorText(verify.error)}
          </p>
        )}
        <button type="submit" className="button w-full" disabled={verify.isPending || code.trim().length < 6}>
          {texts.mfa.submit}
        </button>
      </form>
    </section>
  )
}
