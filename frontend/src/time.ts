// Business dates are calendar days in Europe/Berlin (Lastenheft 0.4 rule 5)
const berlinDate = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Berlin',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** Today in Europe/Berlin as YYYY-MM-DD, e.g. for the max attribute of date inputs (UI-12). */
export function todayInBerlin(now: Date = new Date()): string {
  return berlinDate.format(now)
}

const germanDate = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
const germanDateTime = new Intl.DateTimeFormat('de-DE', {
  timeZone: 'Europe/Berlin',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

/** A calendar date (YYYY-MM-DD) for display, e.g. 03.05.2017 (UI-12). */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return germanDate.format(new Date(year ?? 0, (month ?? 1) - 1, day ?? 1))
}

/** A UTC timestamp for display in Europe/Berlin. */
export function formatDateTime(timestamp: string): string {
  return germanDateTime.format(new Date(timestamp))
}

/** Whole minutes as hours for forms, e.g. 600 → 10 (durations are always minutes in the API, 8.1). */
export function minutesToHours(minutes: number): number {
  return minutes / 60
}

const GERMAN_DATE = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

/** TT.MM.JJJJ (also T.M.JJJJ) to an ISO date; undefined if it is no real calendar date. */
export function fromGermanDate(text: string): string | undefined {
  const match = GERMAN_DATE.exec(text.trim())
  if (match === null) {
    return undefined
  }
  const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const date = new Date(Date.UTC(year, month - 1, day))
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return undefined
  }
  return `${String(year)}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** An ISO date as TT.MM.JJJJ for an input; undefined if it is not an ISO date. */
export function toGermanDate(iso: string): string | undefined {
  const match = ISO_DATE.exec(iso)
  return match === null ? undefined : `${match[3] ?? ''}.${match[2] ?? ''}.${match[1] ?? ''}`
}
