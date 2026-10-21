import { texts } from '../i18n/de'
import { ApiProblem } from './http'

/** German text for an API error, chosen by its stable code (Lastenheft 8.1, UI-02). */
export function errorText(error: unknown): string {
  if (error instanceof ApiProblem && error.code in texts.errors) {
    return texts.errors[error.code as keyof typeof texts.errors]
  }
  return texts.errors.UNKNOWN
}

export function hasCode(error: unknown, code: string): error is ApiProblem {
  return error instanceof ApiProblem && error.code === code
}
