import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { texts } from '../i18n/de'
import { familyMember, problem, signedOut, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const REDEEM = '/api/v1/auth/magic-link/redeem'

function openLink(fragment: string) {
  window.history.replaceState(null, '', `/login/link${fragment}`)
  renderRoute('/login/link')
}

describe('LinkPage', () => {
  it('signs in only on click and removes the token from the address bar', async () => {
    let signedIn = false
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return signedIn ? { status: 200, body: familyMember } : signedOut
      }
      if (method === 'POST' && url === REDEEM) {
        signedIn = true
        return { status: 200, body: { status: 'LOGGED_IN' } }
      }
      return undefined
    })

    openLink('#token=secret-token')
    const button = await screen.findByRole('button', { name: texts.link.submit })

    expect(window.location.hash).toBe('')
    expect(calls.some((call) => call.url === REDEEM)).toBe(false)

    fireEvent.click(button)

    expect(await screen.findByText(texts.home.greeting('Mia Muster'))).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: REDEEM,
      body: { token: 'secret-token', confirmOtherDevice: false },
    })
  })

  it('asks before signing in with a link requested on another device', async () => {
    let signedIn = false
    const calls = stubApi(({ url, body }) => {
      if (url === REDEEM) {
        signedIn = (body as { confirmOtherDevice: boolean }).confirmOtherDevice
        return signedIn
          ? { status: 200, body: { status: 'LOGGED_IN' } }
          : problem(409, 'OTHER_DEVICE_CONFIRMATION_REQUIRED', { maskedEmail: 'm***@example.org' })
      }
      if (url === '/api/v1/me') {
        return signedIn ? { status: 200, body: familyMember } : signedOut
      }
      return undefined
    })

    openLink('#token=secret-token')
    fireEvent.click(await screen.findByRole('button', { name: texts.link.submit }))
    expect(await screen.findByText(texts.link.otherDevice('m***@example.org'))).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: texts.link.confirm }))

    await screen.findByText(texts.home.greeting('Mia Muster'))
    expect(calls.filter((call) => call.url === REDEEM).map((call) => call.body)).toEqual([
      { token: 'secret-token', confirmOtherDevice: false },
      { token: 'secret-token', confirmOtherDevice: true },
    ])
  })

  it('does not sign in when the other-device question is cancelled', async () => {
    const calls = stubApi(({ url }) =>
      url === REDEEM
        ? problem(409, 'OTHER_DEVICE_CONFIRMATION_REQUIRED', { maskedEmail: 'm***@example.org' })
        : signedOut,
    )

    openLink('#token=secret-token')
    fireEvent.click(await screen.findByRole('button', { name: texts.link.submit }))
    fireEvent.click(await screen.findByRole('button', { name: texts.link.cancel }))

    expect(await screen.findByText(texts.link.cancelled)).toBeInTheDocument()
    expect(calls.filter((call) => call.url === REDEEM)).toHaveLength(1)
  })

  it('continues with the second factor for admins with TOTP', async () => {
    stubApi(({ url }) =>
      url === REDEEM ? { status: 200, body: { status: 'MFA_REQUIRED' } } : url === '/api/v1/me' ? signedOut : undefined,
    )

    openLink('#token=secret-token')
    fireEvent.click(await screen.findByRole('button', { name: texts.link.submit }))

    expect(await screen.findByRole('heading', { name: texts.mfa.title })).toBeInTheDocument()
  })

  it('explains an expired or used link', async () => {
    stubApi(({ url }) => (url === REDEEM ? problem(400, 'LINK_INVALID') : signedOut))

    openLink('#token=old-token')
    fireEvent.click(await screen.findByRole('button', { name: texts.link.submit }))

    expect(await screen.findByText(texts.link.invalid)).toBeInTheDocument()
  })

  it('explains a link without token', async () => {
    stubApi(() => signedOut)

    openLink('')

    expect(await screen.findByText(texts.link.missing)).toBeInTheDocument()
  })
})
