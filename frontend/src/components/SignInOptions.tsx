import type { ReactNode } from 'react'
import { useListLoginProviders } from '../api/generated/endpoints/auth/auth'
import { texts } from '../i18n/de'
import { GoogleButton, OrDivider } from './GoogleButton'
import { NetIdButton } from './NetIdButton'

interface SignInOptionsProps {
  purpose: 'login' | 'register'
  /** Further ways above the e-mail form, e.g. the passkey button on the login page. */
  children?: ReactNode
}

/** The buttons of the active providers (`GET /auth/providers`, UI-06), then "oder" before the e-mail form. */
export function SignInOptions({ purpose, children }: SignInOptionsProps) {
  const providers = useListLoginProviders({ query: { staleTime: Infinity } })
  const buttons = (providers.data ?? []).map((provider) => {
    switch (provider.id) {
      case 'google':
        return <GoogleButton key={provider.id} label={purpose === 'login' ? texts.google.login : texts.google.register} />
      case 'netid':
        return <NetIdButton key={provider.id} label={purpose === 'login' ? texts.netid.login : texts.netid.register} />
      default:
        return (
          <a key={provider.id} className="button button--secondary w-full" href={`/oauth2/authorization/${provider.id}`}>
            {texts.providers.with(provider.name)}
          </a>
        )
    }
  })
  if (buttons.length === 0 && children === undefined) {
    return null
  }
  return (
    <>
      <div className="sign-in-options">
        {buttons}
        {children}
      </div>
      <OrDivider />
    </>
  )
}
