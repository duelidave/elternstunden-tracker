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
