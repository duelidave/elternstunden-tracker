import { type FocusEventHandler, useId } from 'react'
import { texts } from '../i18n/de'

interface TimeFieldProps {
  label: string
  /** HH:MM in 24-hour format, or the typed text while it is not complete. */
  value: string
  onChange: (value: string) => void
  onBlur?: FocusEventHandler<HTMLInputElement>
  name?: string
  error?: string | undefined
}

/**
 * Time input in 24-hour format on every device (UI-12): HH:MM with the colon added while typing. Native time inputs
 * follow the device and may show AM/PM.
 */
export function TimeField({ label, value, onChange, onBlur, name, error }: TimeFieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        name={name}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        placeholder={texts.entry.timePlaceholder}
        maxLength={5}
        className="w-28"
        value={value}
        aria-invalid={error !== undefined}
        aria-describedby={error === undefined ? undefined : errorId}
        onChange={(event) => {
          const raw = event.target.value
          const typing = raw.length > value.length
          onChange(typing && /^\d{2}$/.test(raw) ? `${raw}:` : raw)
        }}
        onBlur={onBlur}
      />
      {error !== undefined && (
        <p id={errorId} className="field__error">
          {error}
        </p>
      )}
    </div>
  )
}
