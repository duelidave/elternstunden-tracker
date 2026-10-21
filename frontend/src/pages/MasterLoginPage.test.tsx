import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { texts } from '../i18n/de'
import { familyMember, problem, signedOut, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const master = { ...familyMember, role: 'MASTER_ADMIN', assignedToFamily: false }

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

async function submit(totp: string) {
  await screen.findByRole('heading', { name: texts.master.title })
  fill(texts.common.email, 'master@example.org')
  fill(texts.master.password, 'correct horse battery staple')
  fill(texts.master.totp, totp)
  fireEvent.click(screen.getByRole('button', { name: texts.master.submit }))
}

describe('MasterLoginPage', () => {
  it('signs in with password and TOTP code', async () => {
    let signedIn = false
    const calls = stubApi(({ method, url }) => {
      if (method === 'POST' && url === '/api/v1/auth/master/login') {
        signedIn = true
        return { status: 200, body: master }
      }
      return signedIn ? { status: 200, body: master } : signedOut
    })

    renderRoute('/login/master')
    await submit('123456')

    expect(await screen.findByText(texts.home.master)).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'POST',
      url: '/api/v1/auth/master/login',
      body: { email: 'master@example.org', password: 'correct horse battery staple', totp: '123456' },
    })
  })

  it('checks the code format before sending', async () => {
    const calls = stubApi(() => signedOut)

    renderRoute('/login/master')
    await submit('12ab')

    expect(await screen.findByText(texts.fields.totp)).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })

  it('shows one neutral message for every failed login', async () => {
    stubApi(({ method }) => (method === 'POST' ? problem(401, 'LOGIN_FAILED') : signedOut))

    renderRoute('/login/master')
    await submit('123456')

    expect(await screen.findByRole('alert')).toHaveTextContent(texts.master.failed)
  })
})
