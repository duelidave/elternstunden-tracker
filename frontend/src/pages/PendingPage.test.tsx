import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Me } from '../api/generated/model'
import { texts } from '../i18n/de'
import { familyMember, problem, stubApi } from '../test/api'
import { renderRoute } from '../test/render'

const unassigned: Me = {
  ...familyMember,
  assignedToFamily: false,
  pendingClaim: { firstName: 'Anna', lastName: 'Mueler', birthDate: '2017-05-03' },
  claimCorrectionsLeft: 2,
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('PendingPage', () => {
  it('shows the entered child data and the corrections left', async () => {
    stubApi(({ url }) => (url === '/api/v1/me' ? { status: 200, body: unassigned } : undefined))

    renderRoute('/pending')

    expect(await screen.findByText(texts.pending.child('Anna Mueler', '03.05.2017'))).toBeInTheDocument()
    expect(screen.getByText(texts.pending.correctionsLeft(2))).toBeInTheDocument()
    expect(screen.getByLabelText(texts.register.childLastName)).toHaveValue('Mueler')
    expect(screen.getByLabelText(texts.register.childBirthDate)).toHaveValue('03.05.2017')
  })

  it('sends a correction and goes to the start page once assigned', async () => {
    let me: Me = unassigned
    const calls = stubApi(({ method, url }) => {
      if (method === 'PUT' && url === '/api/v1/me/claim') {
        me = { ...familyMember }
        return { status: 200, body: me }
      }
      return url === '/api/v1/me' ? { status: 200, body: me } : undefined
    })

    renderRoute('/pending')
    await screen.findByText(texts.pending.correctTitle)
    fill(texts.register.childLastName, 'Müller')
    fireEvent.click(screen.getByRole('button', { name: texts.pending.submit }))

    expect(await screen.findByRole('heading', { name: texts.home.greeting('Mia Muster') })).toBeInTheDocument()
    expect(calls).toContainEqual({
      method: 'PUT',
      url: '/api/v1/me/claim',
      body: { firstName: 'Anna', lastName: 'Müller', birthDate: '2017-05-03' },
    })
  })

  it('asks for the child data when there is no claim yet', async () => {
    stubApi(({ url }) =>
      url === '/api/v1/me'
        ? { status: 200, body: { ...unassigned, pendingClaim: undefined, claimCorrectionsLeft: 3 } }
        : undefined,
    )

    renderRoute('/pending')

    expect(await screen.findByRole('heading', { name: texts.pending.enterTitle })).toBeInTheDocument()
    expect(screen.getByText(texts.pending.enterIntro)).toBeInTheDocument()
    expect(screen.queryByText(texts.pending.title)).not.toBeInTheDocument()
    expect(screen.queryByText(texts.pending.intro)).not.toBeInTheDocument()
    expect(screen.queryByText(texts.pending.yourData)).not.toBeInTheDocument()
  })

  it('offers no form when no correction is left', async () => {
    stubApi(({ url }) =>
      url === '/api/v1/me' ? { status: 200, body: { ...unassigned, claimCorrectionsLeft: 0 } } : undefined,
    )

    renderRoute('/pending')

    expect(await screen.findByText(texts.pending.noCorrections)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: texts.pending.submit })).not.toBeInTheDocument()
  })

  it('explains a refused correction', async () => {
    stubApi(({ method, url }) => {
      if (method === 'PUT') {
        return problem(409, 'CLAIM_CORRECTION_LIMIT')
      }
      return url === '/api/v1/me' ? { status: 200, body: unassigned } : undefined
    })

    renderRoute('/pending')
    await screen.findByText(texts.pending.correctTitle)
    fireEvent.click(screen.getByRole('button', { name: texts.pending.submit }))

    expect(await within(screen.getByRole('main')).findByRole('alert')).toHaveTextContent(
      texts.errors.CLAIM_CORRECTION_LIMIT,
    )
  })
})
