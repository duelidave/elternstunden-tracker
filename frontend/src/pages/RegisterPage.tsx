import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { errorText } from '../api/errors'
import { useRegisterAccount } from '../api/generated/endpoints/auth/auth'
import { RegisterAccountBody } from '../api/generated/zod/auth/auth.zod'
import { Captcha, type CaptchaHandle } from '../components/Captcha'
import { MailIcon } from '../components/Icons'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'
import { todayInBerlin } from '../time'

// Same rules as the server (UI-08): the generated contract schema plus the business rules of 7.1.2
const schema = RegisterAccountBody.omit({ captcha: true }).extend({
  privacyAccepted: z.literal(true),
  child: RegisterAccountBody.shape.child.refine((child) => child.birthDate <= todayInBerlin(), {
    path: ['birthDate'],
  }),
})
type RegisterForm = z.input<typeof schema>

/** Registration (FS-REG 7.1.4, UI-06): name, e-mail, child, privacy, captcha. No password. */
export function RegisterPage() {
  const [captcha, setCaptcha] = useState<string | undefined>()
  const [sent, setSent] = useState(false)
  const captchaRef = useRef<CaptchaHandle>(null)
  const register = useRegisterAccount()
  const form = useForm<RegisterForm>({ resolver: zodResolver(schema) })
  const errors = form.formState.errors
  const onSolved = useCallback((payload: string | undefined) => {
    setCaptcha(payload)
  }, [])

  const submit = form.handleSubmit(async (values) => {
    if (captcha === undefined) {
      return
    }
    try {
      await register.mutateAsync({ data: { ...values, privacyAccepted: true, captcha } })
      setSent(true)
    } catch {
      // shown via register.error
    } finally {
      captchaRef.current?.reset()
    }
  })

  if (sent) {
    return (
      <section className="card" aria-live="polite">
        <MailIcon />
        <h2>{texts.register.sentTitle}</h2>
        <p>{texts.register.sent}</p>
      </section>
    )
  }

  return (
    <section className="card">
      <h2>{texts.register.title}</h2>
      <p>{texts.register.intro}</p>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField
          label={texts.register.displayName}
          autoComplete="name"
          error={errors.displayName ? texts.fields.required : undefined}
          {...form.register('displayName')}
        />
        <TextField
          label={texts.common.email}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={errors.email ? texts.fields.email : undefined}
          {...form.register('email')}
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
          <TextField
            label={texts.register.childBirthDate}
            type="date"
            max={todayInBerlin()}
            error={errors.child?.birthDate ? texts.fields.birthDate : undefined}
            {...form.register('child.birthDate')}
          />
        </fieldset>
        <div className="checkbox">
          <input id="privacy" type="checkbox" {...form.register('privacyAccepted')} />
          <label htmlFor="privacy">
            {texts.register.privacyBefore}
            <Link to="/legal/privacy">{texts.register.privacyLink}</Link>
            {texts.register.privacyAfter}
          </label>
        </div>
        {errors.privacyAccepted && <p className="field__error">{texts.fields.privacy}</p>}
        <Captcha ref={captchaRef} onSolved={onSolved} />
        {register.isError && (
          <p className="alert" role="alert">
            {errorText(register.error)}
          </p>
        )}
        <button type="submit" className="button w-full" disabled={captcha === undefined || register.isPending}>
          {texts.register.submit}
        </button>
      </form>
      <p className="card__footer">
        {texts.register.haveAccount} <Link to="/login">{texts.login.title}</Link>
      </p>
    </section>
  )
}
