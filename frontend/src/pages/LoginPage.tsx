import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router'
import { errorText, textFor } from '../api/errors'
import { useRequestMagicLink } from '../api/generated/endpoints/auth/auth'
import { RequestMagicLinkBody } from '../api/generated/zod/auth/auth.zod'
import { Captcha, type CaptchaHandle } from '../components/Captcha'
import { PasskeyLogin } from '../components/PasskeyLogin'
import { SignInOptions } from '../components/SignInOptions'
import { MailIcon } from '../components/Icons'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'

const schema = RequestMagicLinkBody.pick({ email: true })
type LoginForm = { email: string }

/** Login per magic link (UI-06). Google follows with M3 (OIDC). */
export function LoginPage() {
  const [captcha, setCaptcha] = useState<string | undefined>()
  const [sent, setSent] = useState(false)
  const [params] = useSearchParams()
  const providerError = params.get('error')
  const captchaRef = useRef<CaptchaHandle>(null)
  const request = useRequestMagicLink()
  const form = useForm<LoginForm>({ resolver: zodResolver(schema), defaultValues: { email: '' } })
  const onSolved = useCallback((payload: string | undefined) => {
    setCaptcha(payload)
  }, [])

  const submit = form.handleSubmit(async ({ email }) => {
    if (captcha === undefined) {
      return
    }
    try {
      await request.mutateAsync({ data: { email, captcha } })
      setSent(true)
    } catch {
      // shown via request.error
    } finally {
      captchaRef.current?.reset()
    }
  })

  if (sent) {
    return (
      <section className="card" aria-live="polite">
        <MailIcon />
        <h2>{texts.login.sentTitle}</h2>
        <p>{texts.login.sent}</p>
        <button
          type="button"
          className="button button--secondary"
          onClick={() => {
            setSent(false)
          }}
        >
          {texts.login.sendAgain}
        </button>
      </section>
    )
  }

  return (
    <section className="card">
      <h2>{texts.login.title}</h2>
      {providerError !== null && (
        <p className="alert" role="alert">
          {textFor(texts.login.errors, providerError, texts.login.errors.OIDC_FAILED)}
        </p>
      )}
      <SignInOptions purpose="login">
        <PasskeyLogin />
      </SignInOptions>
      <p>{texts.login.intro}</p>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField
          label={texts.common.email}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={form.formState.errors.email ? texts.fields.email : undefined}
          {...form.register('email')}
        />
        <Captcha ref={captchaRef} onSolved={onSolved} />
        {request.isError && (
          <p className="alert" role="alert">
            {errorText(request.error)}
          </p>
        )}
        <button type="submit" className="button w-full" disabled={captcha === undefined || request.isPending}>
          {texts.login.submit}
        </button>
      </form>
      <p className="card__footer">
        {texts.login.noAccount} <Link to="/register">{texts.login.toRegister}</Link>
      </p>
    </section>
  )
}
