import 'react-day-picker/style.css'
import { type FocusEventHandler, useId, useRef, useState } from 'react'
import { DayPicker } from 'react-day-picker'
import { de } from 'react-day-picker/locale'
import { texts } from '../i18n/de'
import { fromGermanDate, toGermanDate } from '../time'

interface DateFieldProps {
  label: string
  /** ISO date (YYYY-MM-DD), or the typed text while it is not a valid date yet. */
  value: string
  onChange: (value: string) => void
  onBlur?: FocusEventHandler<HTMLInputElement>
  name?: string
  /** Latest selectable date (ISO). */
  max?: string
  /** Earliest selectable date (ISO). */
  min?: string
  error?: string | undefined
}

/**
 * Date input in German format on every device (UI-12, D-27): TT.MM.JJJJ with the dots added while typing, plus a
 * German calendar in a dialog. The form value is the ISO date the API expects; text that is not a valid date is
 * passed on unchanged, so the form validation (UI-08) reports it.
 */
export function DateField({ label, value, onChange, onBlur, name, max, min, error }: DateFieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const dialog = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const [text, setText] = useState(() => toGermanDate(value) ?? value)
  const [seenValue, setSeenValue] = useState(value)
  const [open, setOpen] = useState(false)

  // Follow outside changes, e.g. a form reset or a date picked in the calendar (adjusting state during render)
  if (value !== seenValue) {
    setSeenValue(value)
    const german = toGermanDate(value)
    if (german !== undefined || value === '') {
      setText(german ?? '')
    }
  }

  const type = (raw: string) => {
    const typing = raw.length > text.length
    const next = typing && /^\d{2}$|^\d{2}\.\d{2}$/.test(raw) ? `${raw}.` : raw
    setText(next)
    onChange(next === '' ? '' : (fromGermanDate(next) ?? next))
  }

  const show = () => {
    setOpen(true)
    // showModal: focus stays in the dialog, Escape closes it (not available in every test DOM)
    queueMicrotask(() => dialog.current?.showModal?.())
  }

  const close = () => {
    dialog.current?.close?.()
    setOpen(false)
    input.current?.focus()
  }

  const selected = toGermanDate(value) === undefined ? undefined : isoToDate(value)
  const latest = max === undefined ? undefined : isoToDate(max)

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="date-field">
        <input
          id={id}
          ref={input}
          name={name}
          type="text"
          inputMode="numeric"
          autoComplete="off"
          placeholder={texts.fields.datePlaceholder}
          maxLength={10}
          value={text}
          aria-invalid={error !== undefined}
          aria-describedby={error === undefined ? undefined : errorId}
          onChange={(event) => {
            type(event.target.value)
          }}
          onBlur={onBlur}
        />
        <button
          type="button"
          className="date-field__toggle"
          aria-label={texts.fields.openCalendar(label)}
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={show}
        >
          <CalendarIcon />
        </button>
      </div>
      {error !== undefined && (
        <p id={errorId} className="field__error">
          {error}
        </p>
      )}
      <dialog
        ref={dialog}
        className="date-dialog"
        aria-label={texts.fields.calendar(label)}
        onClose={() => {
          setOpen(false)
        }}
        onClick={(event) => {
          // A click on the backdrop (the dialog element itself) closes it
          if (event.target === dialog.current) {
            close()
          }
        }}
      >
        {open && (
          <>
            <DayPicker
              mode="single"
              locale={de}
              captionLayout="dropdown"
              selected={selected}
              defaultMonth={selected ?? latest}
              startMonth={min === undefined ? new Date(1990, 0) : isoToDate(min)}
              endMonth={latest ?? new Date(new Date().getFullYear() + 5, 11)}
              disabled={[
                ...(latest === undefined ? [] : [{ after: latest }]),
                ...(min === undefined ? [] : [{ before: isoToDate(min) }]),
              ]}
              onSelect={(date) => {
                if (date !== undefined) {
                  onChange(dateToIso(date))
                }
                close()
              }}
            />
            <div className="actions mt-2 justify-end">
              <button type="button" className="button button--secondary" onClick={close}>
                {texts.admin.cancel}
              </button>
            </div>
          </>
        )}
      </dialog>
    </div>
  )
}

function isoToDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1)
}

function dateToIso(date: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="size-6">
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" fill="none" strokeWidth="2" className="stroke-text" />
      <path d="M3.5 10h17M8 3v4M16 3v4" fill="none" strokeWidth="2" strokeLinecap="round" className="stroke-text" />
    </svg>
  )
}
