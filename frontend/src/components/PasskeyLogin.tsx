import { browserSupportsWebAuthn, startAuthentication, type PublicKeyCredentialRequestOptionsJSON } from '@simplewebauthn/browser'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useLoginWithPasskey, useStartPasskeyLogin } from '../api/generated/endpoints/auth/auth'
import type { WebAuthnJson } from '../api/generated/model'
import { useSessionChanged } from '../api/session'
import { texts } from '../i18n/de'
import { nextPath } from '../pages/loginResult'

/** "Mit Passkey anmelden" (7.2, D-30): no e-mail needed, the browser offers the passkeys of this site. */
export function PasskeyLogin() {
  const start = useStartPasskeyLogin()
  const login = useLoginWithPasskey()
  const refreshSession = useSessionChanged()
  const navigate = useNavigate()
  const [failed, setFailed] = useState(false)

  if (!browserSupportsWebAuthn()) {
    return null
  }

  const signIn = async () => {
    setFailed(false)
    try {
      const { challengeId, options } = await start.mutateAsync()
      const credential = await startAuthentication({
        optionsJSON: options as unknown as PublicKeyCredentialRequestOptionsJSON,
      })
      const result = await login.mutateAsync({
        data: { challengeId, credential: credential as unknown as WebAuthnJson },
      })
      await refreshSession()
      await navigate(nextPath(result), { replace: true })
    } catch (error) {
      // Cancelled in the browser's dialog: nothing to say
      if (!(error instanceof Error && error.name === 'NotAllowedError')) {
        setFailed(true)
      }
    }
  }

  return (
    <div>
      <button
        type="button"
        className="button button--secondary w-full"
        disabled={start.isPending || login.isPending}
        onClick={() => void signIn()}
      >
        <KeyIcon />
        <span>{texts.passkey.login}</span>
      </button>
      {failed && (
        <p className="alert mt-2" role="alert">
          {texts.passkey.failed}
        </p>
      )}
    </div>
  )
}

export function KeyIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="mr-2 size-6">
      <circle cx="8" cy="12" r="4" fill="none" strokeWidth="2" className="stroke-text" />
      <path d="M12 12h9m-3 0v3m-3-3v2" fill="none" strokeWidth="2" strokeLinecap="round" className="stroke-text" />
    </svg>
  )
}
