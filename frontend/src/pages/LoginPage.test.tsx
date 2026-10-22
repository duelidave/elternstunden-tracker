import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { texts } from '../i18n/de'
import { todayInBerlin, toGermanDate } from '../time'
import { CAPTCHA_PAYLOAD, FakeCaptcha } from '../test/captcha'
import { problem, signedOut, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

vi.mock('../components/Captcha', () => ({ Captcha: FakeCaptcha }))

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('LoginPage', () => {
  it('requests a magic link with the captcha solution', async () => {
    const calls = stubApi(({ method, url }) => {
      if (method === 'POST' && url === '/api/v1/auth/magic-link') {
        return { status: 202 }
      }
      return signedOut
    })

    renderRoute('/login')
    await screen.findByRole('heading', { name: texts.login.title })
    fill(texts.common.email, 'mia@example.org')
    fireEvent.click(screen.getByRole('button', { name: texts.login.submit }))

    expect(await screen.findByText(texts.login.sent)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/auth/magic-link',
      body: { email: 'mia@example.org', captcha: CAPTCHA_PAYLOAD },
    })
  })

  it('checks the e-mail address before sending', async () => {
    const calls = stubApi(() => signedOut)

    renderRoute('/login')
    await screen.findByRole('heading', { name: texts.login.title })
    fill(texts.common.email, 'not-an-address')
    fireEvent.click(screen.getByRole('button', { name: texts.login.submit }))

    expect(await screen.findByText(texts.fields.email)).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })

  it('explains the rate limit', async () => {
    stubApi(({ method }) => (method === 'POST' ? problem(429, 'RATE_LIMITED') : signedOut))

    renderRoute('/login')
    await screen.findByRole('heading', { name: texts.login.title })
    fill(texts.common.email, 'mia@example.org')
    fireEvent.click(screen.getByRole('button', { name: texts.login.submit }))

    expect(await screen.findByRole('alert')).toHaveTextContent(texts.errors.RATE_LIMITED)
  })
})

describe('RegisterPage', () => {
  async function fillForm(birthDate: string) {
    await screen.findByRole('heading', { name: texts.register.title })
    fill(texts.register.displayName, 'Mia Muster')
    fill(texts.common.email, 'mia@example.org')
    fill(texts.register.childFirstName, 'Lena')
    fill(texts.register.childLastName, 'Muster')
    fill(texts.register.childBirthDate, birthDate)
  }

  it('registers with child data, privacy consent and captcha', async () => {
    const calls = stubApi(({ method, url }) => {
      if (method === 'POST' && url === '/api/v1/auth/register') {
        return { status: 202 }
      }
      return signedOut
    })

    renderRoute('/register')
    await fillForm('04.05.2019')
    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(screen.getByRole('button', { name: texts.register.submit }))

    expect(await screen.findByText(texts.register.sent)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/auth/register',
      body: {
        displayName: 'Mia Muster',
        email: 'mia@example.org',
        child: { firstName: 'Lena', lastName: 'Muster', birthDate: '2019-05-04' },
        privacyAccepted: true,
        captcha: CAPTCHA_PAYLOAD,
      },
    })
  })

  it('needs the privacy consent and a birth date that is not in the future', async () => {
    const calls = stubApi(() => signedOut)
    const tomorrow = new Date(Date.now() + 36 * 60 * 60 * 1000)

    renderRoute('/register')
    await fillForm(toGermanDate(todayInBerlin(tomorrow)) ?? '')
    fireEvent.click(screen.getByRole('button', { name: texts.register.submit }))

    expect(await screen.findByText(texts.fields.privacy)).toBeInTheDocument()
    expect(screen.getByText(texts.fields.birthDate)).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })
})
