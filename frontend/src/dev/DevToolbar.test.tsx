import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Me } from '../api/generated/model'
import { texts } from '../i18n/de'
import { familyMember, signedOut, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const admin: Me = { ...familyMember, displayName: 'Demo Admin', email: 'admin@dev.local', role: 'ADMIN' }

describe('DevToolbar', () => {
  it('switches to a demo account and shows it', async () => {
    let me: Me | undefined
    const calls = stubApi(({ method, url }) => {
      if (method === 'POST' && url === '/api/v1/dev/login-as') {
        me = admin
        return { status: 200, body: admin }
      }
      return me === undefined ? signedOut : { status: 200, body: me }
    })

    renderRoute('/legal/privacy')
    const toolbar = within(await screen.findByRole('complementary', { name: 'Dev: Rolle wechseln' }))
    expect(toolbar.getByText('Nicht angemeldet', { exact: false })).toBeInTheDocument()

    fireEvent.click(toolbar.getByRole('button', { name: 'Admin' }))

    expect(await screen.findByText(texts.home.admin)).toBeInTheDocument()
    expect(toolbar.getByText('ADMIN: admin@dev.local', { exact: false })).toBeInTheDocument()
    expect(calls).toContainEqual({ method: 'POST', url: '/api/v1/dev/login-as', body: { account: 'ADMIN' } })
  })
})
