import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import type { z } from 'zod'
import { errorText, hasCode } from '../api/errors'
import { useLoginMaster } from '../api/generated/endpoints/auth/auth'
import { LoginMasterBody } from '../api/generated/zod/auth/auth.zod'
import { useRefreshSession } from '../api/session'
import { TextField } from '../components/TextField'
import { texts } from '../i18n/de'

type MasterForm = z.input<typeof LoginMasterBody>

/** Master login with password and TOTP (7.2, 7.9). Not linked from /login. */
export function MasterLoginPage() {
  const login = useLoginMaster()
  const refreshSession = useRefreshSession()
  const navigate = useNavigate()
  const form = useForm<MasterForm>({
    resolver: zodResolver(LoginMasterBody),
    defaultValues: { email: '', password: '', totp: '' },
  })
  const errors = form.formState.errors

  const submit = form.handleSubmit(async (values) => {
    try {
      await login.mutateAsync({ data: values })
      await refreshSession()
      await navigate('/', { replace: true })
    } catch {
      // shown via login.error
    } finally {
      form.resetField('totp')
    }
  })

  return (
    <section className="card">
      <h2>{texts.master.title}</h2>
      <form onSubmit={(event) => void submit(event)} noValidate>
        <TextField
          label={texts.common.email}
          type="email"
          autoComplete="username"
          error={errors.email ? texts.fields.email : undefined}
          {...form.register('email')}
        />
        <TextField
          label={texts.master.password}
          type="password"
          autoComplete="current-password"
          error={errors.password ? texts.fields.required : undefined}
          {...form.register('password')}
        />
        <TextField
          label={texts.master.totp}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          error={errors.totp ? texts.fields.totp : undefined}
          {...form.register('totp')}
        />
        {login.isError && (
          <p className="alert" role="alert">
            {hasCode(login.error, 'LOGIN_FAILED') ? texts.master.failed : errorText(login.error)}
          </p>
        )}
        <button type="submit" className="button" disabled={login.isPending}>
          {texts.master.submit}
        </button>
      </form>
    </section>
  )
}
