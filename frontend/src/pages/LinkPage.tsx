import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { errorText, hasCode } from '../api/errors'
import { useRedeemMagicLink } from '../api/generated/endpoints/auth/auth'
import { useSessionChanged } from '../api/session'
import { texts } from '../i18n/de'
import { nextPath } from './loginResult'

/**
 * The token comes from the URL fragment (7.2, SEC-17): it never reaches the server, and a plain page view (e.g. a
 * mail scanner) never redeems anything, only the click on the button does.
 */
function tokenFromFragment(): string | undefined {
  return new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token') ?? undefined
}

export function LinkPage() {
  const [token] = useState(tokenFromFragment)
  // Remove the token from the address bar right away (history, screenshots, shoulder surfing)
  useEffect(() => {
    if (window.location.hash !== '') {
      window.history.replaceState(null, '', window.location.pathname + window.location.search)
    }
  }, [])
  const [cancelled, setCancelled] = useState(false)
  const redeem = useRedeemMagicLink()
  const refreshSession = useSessionChanged()
  const navigate = useNavigate()

  const signIn = async (confirmOtherDevice: boolean) => {
    if (token === undefined) {
      return
    }
    try {
      const result = await redeem.mutateAsync({ data: { token, confirmOtherDevice } })
      await refreshSession()
      await navigate(nextPath(result), { replace: true })
    } catch {
      // shown below
    }
  }

  if (token === undefined) {
    return <Message text={texts.link.missing} />
  }
  if (cancelled) {
    return <Message text={texts.link.cancelled} />
  }

  const otherDevice = hasCode(redeem.error, 'OTHER_DEVICE_CONFIRMATION_REQUIRED') ? redeem.error : undefined
  if (otherDevice !== undefined) {
    const masked = typeof otherDevice.properties.maskedEmail === 'string' ? otherDevice.properties.maskedEmail : ''
    return (
      <section className="card">
        <h2>{texts.link.title}</h2>
        <p role="alert">{texts.link.otherDevice(masked)}</p>
        <div className="actions">
          <button type="button" className="button" disabled={redeem.isPending} onClick={() => void signIn(true)}>
            {texts.link.confirm}
          </button>
          <button
            type="button"
            className="button button--secondary"
            onClick={() => {
              setCancelled(true)
            }}
          >
            {texts.link.cancel}
          </button>
        </div>
      </section>
    )
  }

  if (redeem.isError) {
    return <Message text={hasCode(redeem.error, 'LINK_INVALID') ? texts.link.invalid : errorText(redeem.error)} />
  }

  return (
    <section className="card">
      <h2>{texts.link.title}</h2>
      <p>{texts.link.intro}</p>
      <button
        type="button"
        className="button w-full"
        disabled={redeem.isPending}
        onClick={() => void signIn(false)}
      >
        {texts.link.submit}
      </button>
    </section>
  )
}

function Message({ text }: { text: string }) {
  return (
    <section className="card" aria-live="polite">
      <h2>{texts.link.title}</h2>
      <p>{text}</p>
      <Link className="button button--secondary" to="/login">
        {texts.common.backToLogin}
      </Link>
    </section>
  )
}
