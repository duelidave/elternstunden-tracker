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
