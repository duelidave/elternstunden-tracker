import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { texts } from '../i18n/de'
import { fromGermanDate, toGermanDate } from '../time'
import { DateField } from './DateField'

function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <DateField label="Geburtsdatum" value={value} onChange={setValue} max="2026-10-22" />
      <output data-testid="value">{value}</output>
    </>
  )
}

function input() {
  return screen.getByLabelText('Geburtsdatum')
}

function typeEach(text: string) {
  let current = ''
  for (const character of text) {
    current += character
    fireEvent.change(input(), { target: { value: current } })
    current = (input() as HTMLInputElement).value
  }
}

describe('DateField', () => {
  it('shows an ISO value in German format', () => {
    render(<Harness initial="2025-03-17" />)

    expect(input()).toHaveValue('17.03.2025')
    expect(input()).toHaveAttribute('placeholder', texts.fields.datePlaceholder)
  })

  it('adds the dots while typing and passes the ISO date on', () => {
    render(<Harness />)

    typeEach('17032025')

    expect(input()).toHaveValue('17.03.2025')
    expect(screen.getByTestId('value')).toHaveTextContent('2025-03-17')
  })

  it('passes text that is no real date on unchanged, so validation can report it', () => {
    render(<Harness />)

    fireEvent.change(input(), { target: { value: '31.02.2025' } })

    expect(screen.getByTestId('value')).toHaveTextContent('31.02.2025')
  })

  it('offers a calendar button', () => {
    render(<Harness />)

    const toggle = screen.getByRole('button', { name: texts.fields.openCalendar('Geburtsdatum') })
    fireEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
  })
})

describe('German dates', () => {
  it.each([
    ['17.03.2025', '2025-03-17'],
    ['1.2.2020', '2020-02-01'],
    ['29.02.2024', '2024-02-29'],
  ])('reads %s', (german, iso) => {
    expect(fromGermanDate(german)).toBe(iso)
  })

  it.each(['29.02.2025', '32.01.2025', '2025-03-17', '17.03.25', ''])('rejects %s', (text) => {
    expect(fromGermanDate(text)).toBeUndefined()
  })

  it('writes ISO dates in German', () => {
    expect(toGermanDate('2017-05-03')).toBe('03.05.2017')
    expect(toGermanDate('03.05.2017')).toBeUndefined()
  })
})
