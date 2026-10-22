import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/generated/model'
import { texts } from '../i18n/de'
import { familyMember, problem, signedOut, stubApi } from '../test/api'
import { FakeCaptcha } from '../test/captcha'
import { renderRoute } from '../test/render'

vi.mock('../components/Captcha', () => ({ Captcha: FakeCaptcha }))

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('LoginPage with Google', () => {
  it('offers Google as a plain link and explains a refused provider login', async () => {
    stubApi(() => signedOut)

    renderRoute('/login?error=OIDC_NOT_AUTHORITATIVE')

    const google = await screen.findByRole('link', { name: texts.google.login })
    expect(google).toHaveAttribute('href', '/oauth2/authorization/google')
    expect(screen.getByText(texts.login.errors.OIDC_NOT_AUTHORITATIVE)).toHaveAttribute('role', 'alert')
    expect(screen.getByText(texts.google.hint)).toBeInTheDocument()
  })
})

describe('OidcClaimPage', () => {
  it('completes the registration with the child data', async () => {
    let signedIn = false
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/auth/oidc/pending') {
        return { status: 200, body: { provider: 'google', email: 'mia@gmail.com', displayName: 'Mia Muster' } }
      }
      if (method === 'POST' && url === '/api/v1/auth/oidc/complete') {
        signedIn = true
        return { status: 200, body: { status: 'LOGGED_IN' } }
      }
      return url === '/api/v1/me' ? (signedIn ? { status: 200, body: familyMember } : signedOut) : undefined
    })

    renderRoute('/register/claim')
    expect(await screen.findByText(texts.oidcClaim.account('mia@gmail.com'))).toBeInTheDocument()
    expect(screen.getByLabelText(texts.register.displayName)).toHaveValue('Mia Muster')
    fill(texts.register.childFirstName, 'Lena')
    fill(texts.register.childLastName, 'Muster')
    fill(texts.register.childBirthDate, '04.05.2019')
    fireEvent.click(screen.getByRole('button', { name: texts.oidcClaim.submit }))

    expect(await screen.findByRole('heading', { name: texts.home.greeting('Mia Muster') })).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/auth/oidc/complete',
      body: { displayName: 'Mia Muster', child: { firstName: 'Lena', lastName: 'Muster', birthDate: '2019-05-04' } },
    })
  })

  it('explains an expired pending login', async () => {
    stubApi(() => signedOut)

    renderRoute('/register/claim')

    expect(await screen.findByText(texts.oidcClaim.expired)).toBeInTheDocument()
  })
})

describe('MfaPage', () => {
  it('verifies the code and continues', async () => {
    let signedIn = false
    const admin: Me = { ...familyMember, role: 'ADMIN', assignedToFamily: false }
    stubApi(({ method, url, body }) => {
      if (method === 'POST' && url === '/api/v1/auth/mfa/verify') {
        if ((body as { code: string }).code !== '123456') {
          return problem(400, 'MFA_INVALID')
        }
        signedIn = true
        return { status: 200, body: { status: 'LOGGED_IN' } }
      }
      return url === '/api/v1/me' ? (signedIn ? { status: 200, body: admin } : signedOut) : undefined
    })

    renderRoute('/login/mfa')
    await screen.findByLabelText(texts.mfa.code)
    fill(texts.mfa.code, '654321')
    fireEvent.click(screen.getByRole('button', { name: texts.mfa.submit }))
    expect(await screen.findByRole('alert')).toHaveTextContent(texts.errors.MFA_INVALID)
    fill(texts.mfa.code, '123456')
    fireEvent.click(screen.getByRole('button', { name: texts.mfa.submit }))

    expect(await screen.findByText(texts.home.admin)).toBeInTheDocument()
  })
})

describe('ProfilePage', () => {
  it('shows the account, starts linking Google and offers TOTP to admins', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    const admin: Me = { ...familyMember, role: 'ADMIN', identities: [], totpEnabled: false, totpRequired: false }
    stubApi(({ method, url }) => {
      if (method === 'POST' && url === '/api/v1/me/identities/google/link') {
        return { status: 200, body: { redirectUrl: '/oauth2/authorization/google' } }
      }
      return url === '/api/v1/me' ? { status: 200, body: admin } : undefined
    })

    renderRoute('/profile')
    const main = within(await screen.findByRole('main'))
    expect(await main.findByText(admin.email)).toBeInTheDocument()
    expect(main.getByRole('heading', { name: texts.totp.title })).toBeInTheDocument()
    fireEvent.click(main.getByRole('button', { name: texts.profile.linkProvider('Google') }))

    await vi.waitFor(() => {
      expect(assign).toHaveBeenCalledWith('/oauth2/authorization/google')
    })
  })

  it('asks for a fresh login when linking is too late', async () => {
    stubApi(({ method, url }) => {
      if (method === 'POST' && url === '/api/v1/me/identities/google/link') {
        return problem(403, 'REAUTH_REQUIRED')
      }
      return url === '/api/v1/me' ? { status: 200, body: { ...familyMember, identities: [] } } : undefined
    })

    renderRoute('/profile')
    fireEvent.click(await screen.findByRole('button', { name: texts.profile.linkProvider('Google') }))

    expect(await screen.findByText(texts.errors.REAUTH_REQUIRED)).toBeInTheDocument()
  })

  it('shows a linked provider with the way to remove it', async () => {
    const calls = stubApi(({ method, url }) => {
      if (method === 'DELETE' && url === '/api/v1/me/identities/identity-1') {
        return { status: 204 }
      }
      return url === '/api/v1/me'
        ? {
            status: 200,
            body: {
              ...familyMember,
              identities: [
                { id: 'identity-1', provider: 'google', emailAtLink: 'mia@gmail.com', createdAt: '2026-10-01T10:00:00Z' },
              ],
            },
          }
        : undefined
    })

    renderRoute('/profile')
    expect(await screen.findByText(texts.profile.providerSince('mia@gmail.com'))).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: texts.profile.unlink }))

    await vi.waitFor(() => {
      expect(calls).toContainEqual({ method: 'DELETE', url: '/api/v1/me/identities/identity-1', body: undefined })
    })
  })
})
