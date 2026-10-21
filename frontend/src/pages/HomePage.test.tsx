import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { texts } from '../i18n/de'
import { FakeCaptcha } from '../test/captcha'
import { familyMember, problem, signedOut, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

vi.mock('../components/Captcha', () => ({ Captcha: FakeCaptcha }))

describe('HomePage', () => {
  it('sends visitors without session to the login', async () => {
    stubApi(({ url }) => (url === '/api/v1/me' ? signedOut : undefined))

    const router = renderRoute('/')

    expect(await screen.findByRole('heading', { name: texts.login.title })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it.each([
    [familyMember, texts.home.family],
    [{ ...familyMember, assignedToFamily: false }, texts.home.pending],
    [{ ...familyMember, role: 'ADMIN' }, texts.home.admin],
    [{ ...familyMember, role: 'MASTER_ADMIN' }, texts.home.master],
  ])('greets by role and assignment (%#)', async (me, text) => {
    stubApi(({ url }) => (url === '/api/v1/me' ? { status: 200, body: me } : undefined))

    renderRoute('/')

    expect(await screen.findByText(texts.home.greeting('Mia Muster'))).toBeInTheDocument()
    expect(screen.getByText(text)).toBeInTheDocument()
  })

  it('logs out and returns to the login', async () => {
    let signedIn = true
    const calls = stubApi(({ method, url }) => {
      if (url === '/api/v1/me') {
        return signedIn ? { status: 200, body: familyMember } : signedOut
      }
      if (method === 'POST' && url === '/api/v1/auth/logout') {
        signedIn = false
        return { status: 204 }
      }
      return undefined
    })

    renderRoute('/')
    const main = within(screen.getByRole('main'))
    fireEvent.click(await main.findByRole('button', { name: texts.common.logout }))

    expect(await screen.findByRole('heading', { name: texts.login.title })).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'POST', url: '/api/v1/auth/logout', body: undefined })
  })

  it('says so when the server is not reachable', async () => {
    stubApi(() => problem(500, 'INTERNAL_ERROR'))

    renderRoute('/')

    expect(await screen.findByRole('alert')).toHaveTextContent(texts.errors.UNKNOWN)
  })

  it('links privacy notice and imprint on every screen (UI-13)', async () => {
    stubApi(() => signedOut)

    renderRoute('/legal/imprint')

    expect(await screen.findByRole('heading', { name: texts.legal.imprintTitle })).toBeInTheDocument()
    const footer = within(screen.getByRole('navigation', { name: texts.common.legalNav }))
    expect(footer.getByRole('link', { name: texts.common.privacy })).toHaveAttribute('href', '/legal/privacy')
    expect(footer.getByRole('link', { name: texts.common.imprint })).toHaveAttribute('href', '/legal/imprint')
  })
})
