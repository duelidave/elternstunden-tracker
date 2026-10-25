import { describe, expect, it } from 'vitest'
import { texts } from '../i18n/de'
import { errorText, textFor } from './errors'
import { ApiProblem } from './http'

describe('error texts', () => {
  it('shows the text of a known code and a general one for unknown codes', () => {
    expect(errorText(new ApiProblem(409, { code: 'FAMILY_FULL' }))).toBe(texts.errors.FAMILY_FULL)
    // A code from a newer server version than this client knows
    const future = { code: 'SOMETHING_NEW' } as unknown as { code: 'CONFLICT' }
    expect(errorText(new ApiProblem(400, future))).toBe(texts.errors.UNKNOWN)
    expect(errorText(new Error('network'))).toBe(texts.errors.UNKNOWN)
  })

  it('ignores inherited keys from the address bar, e.g. __proto__', () => {
    expect(textFor(texts.login.errors, '__proto__', 'fallback')).toBe('fallback')
    expect(textFor(texts.login.errors, 'toString', 'fallback')).toBe('fallback')
    expect(textFor(texts.login.errors, 'OIDC_FAILED', 'fallback')).toBe(texts.login.errors.OIDC_FAILED)
  })
})
