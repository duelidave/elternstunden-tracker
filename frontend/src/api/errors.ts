import type { ProblemCode } from './generated/model'
import { texts } from '../i18n/de'
import { ApiProblem } from './http'

// One text for every code of the contract (8.1): the type check fails if a code has no text
const errorTexts: Record<ProblemCode, string> = texts.errors

/** German text for an API error, chosen by its stable code (Lastenheft 8.1, UI-02). */
export function errorText(error: unknown): string {
  if (error instanceof ApiProblem && Object.hasOwn(errorTexts, error.code)) {
    return errorTexts[error.code as ProblemCode]
  }
  return texts.errors.UNKNOWN
}

/** A text from a fixed table for a code from the address bar; own keys only, so "__proto__" finds nothing. */
export function textFor<T extends Record<string, string>>(table: T, code: string, fallback: string): string {
  return Object.hasOwn(table, code) ? (table[code] as string) : fallback
}

export function hasCode(error: unknown, code: string): error is ApiProblem {
  return error instanceof ApiProblem && error.code === code
}
