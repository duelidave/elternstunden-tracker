import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { texts } from '../i18n/de'
import { type ApiCall, familyMember, problem, signedOut, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const webauthn = vi.hoisted(() => ({
  supported: true,
  startAuthentication: vi.fn(),
  startRegistration: vi.fn(),
}))

vi.mock('@simplewebauthn/browser', () => ({
  browserSupportsWebAuthn: () => webauthn.supported,
  startAuthentication: webauthn.startAuthentication,
  startRegistration: webauthn.startRegistration,
}))

const challenge = { challengeId: '00000000-0000-4000-8000-0000000000c1', options: { challenge: 'abc' } }
const assertion = { id: 'cred-1', rawId: 'cred-1', type: 'public-key', response: {} }

afterEach(() => {
  webauthn.supported = true
  vi.restoreAllMocks()
})

describe('passkeys (7.2, D-30)', () => {
  it('signs in with a passkey without an e-mail', async () => {
    let signedIn = false
    webauthn.startAuthentication.mockResolvedValue(assertion)
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return signedIn ? { status: 200, body: familyMember } : signedOut
      }
      if (method === 'POST' && url === '/api/v1/auth/passkey/options') {
        return { status: 200, body: challenge }
      }
      if (method === 'POST' && url === '/api/v1/auth/passkey/login') {
        signedIn = true
        return { status: 200, body: { status: 'LOGGED_IN' } }
      }
      return undefined
    })
    const router = renderRoute('/login')

    fireEvent.click(await screen.findByRole('button', { name: texts.passkey.login }))

    await vi.waitFor(() => {
      expect(router.state.location.pathname).toBe('/')
    })
    expect(webauthn.startAuthentication).toHaveBeenCalledWith({ optionsJSON: challenge.options })
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/auth/passkey/login',
      body: { challengeId: challenge.challengeId, credential: assertion },
    })
  })

  it('says nothing when the dialog is cancelled, but explains a failed login', async () => {
    const cancelled = Object.assign(new Error('cancelled'), { name: 'NotAllowedError' })
    webauthn.startAuthentication.mockRejectedValueOnce(cancelled).mockResolvedValueOnce(assertion)
    stubApi(({ url }) => {
      if (url === '/api/v1/me') {
        return signedOut
      }
      if (url === '/api/v1/auth/passkey/options') {
        return { status: 200, body: challenge }
      }
      return url === '/api/v1/auth/passkey/login' ? problem(401, 'LOGIN_FAILED') : undefined
    })
    renderRoute('/login')

    const button = await screen.findByRole('button', { name: texts.passkey.login })
    fireEvent.click(button)
    await vi.waitFor(() => {
      expect(webauthn.startAuthentication).toHaveBeenCalledTimes(1)
    })
    expect(screen.queryByText(texts.passkey.failed)).not.toBeInTheDocument()

    fireEvent.click(button)
    expect(await screen.findByText(texts.passkey.failed)).toBeInTheDocument()
  })

  it('hides the passkey button without browser support', async () => {
    webauthn.supported = false
    stubApi(({ url }) => (url === '/api/v1/me' ? signedOut : undefined))

    renderRoute('/login')

    expect(await screen.findByRole('link', { name: texts.google.login })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: texts.passkey.login })).not.toBeInTheDocument()
  })

  it('creates, lists and removes passkeys in the profile', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const attestation = { id: 'cred-2', rawId: 'cred-2', type: 'public-key', response: {} }
    webauthn.startRegistration.mockResolvedValue(attestation)
    let passkeys: unknown[] = []
    const calls = stubApi(({ method, url }: ApiCall) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: familyMember }
      }
      if (url === '/api/v1/me/passkeys' && method === 'GET') {
        return { status: 200, body: passkeys }
      }
      if (url === '/api/v1/me/passkeys/options') {
        return { status: 200, body: challenge }
      }
      if (url === '/api/v1/me/passkeys' && method === 'POST') {
        passkeys = [{ id: 'cred-2', label: 'iPhone', createdAt: '2026-10-24T08:00:00Z' }]
        return { status: 201, body: passkeys[0] }
      }
      if (method === 'DELETE') {
        passkeys = []
        return { status: 204 }
      }
      return undefined
    })
    renderRoute('/profile')

    const section = within(
      (await screen.findByRole('heading', { name: texts.passkey.title })).closest('section') as HTMLElement,
    )
    expect(await section.findByText(texts.passkey.none)).toBeInTheDocument()
    fireEvent.change(section.getByLabelText(texts.passkey.label), { target: { value: 'iPhone' } })
    fireEvent.click(section.getByRole('button', { name: texts.passkey.add }))

    expect(await section.findByText(texts.passkey.added)).toBeInTheDocument()
    expect(await section.findByText('iPhone')).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/me/passkeys',
      body: { challengeId: challenge.challengeId, credential: attestation, label: 'iPhone' },
    })

    fireEvent.click(section.getByRole('button', { name: texts.passkey.remove }))
    expect(await section.findByText(texts.passkey.removed)).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'DELETE', url: '/api/v1/me/passkeys/cred-2', body: undefined })
  })

  it('asks for a fresh login before creating a passkey', async () => {
    stubApi(({ url }) => {
      if (url === '/api/v1/me') {
        return { status: 200, body: familyMember }
      }
      if (url === '/api/v1/me/passkeys') {
        return { status: 200, body: [] }
      }
      return url === '/api/v1/me/passkeys/options' ? problem(403, 'REAUTH_REQUIRED') : undefined
    })
    renderRoute('/profile')

    fireEvent.click(await screen.findByRole('button', { name: texts.passkey.add }))

    expect(await screen.findByText(texts.errors.REAUTH_REQUIRED)).toBeInTheDocument()
    expect(webauthn.startRegistration).not.toHaveBeenCalled()
  })
})
