import { type ComponentProps, useId } from 'react'

type TextFieldProps = ComponentProps<'input'> & {
  label: string
  error?: string | undefined
}

/** Labelled input with an accessible error text (UI-09). */
export function TextField({ label, error, id, ...input }: TextFieldProps) {
  const generated = useId()
  const inputId = id ?? generated
  const errorId = `${inputId}-error`
  return (
    <div className="field">
      <label htmlFor={inputId}>{label}</label>
      <input
        id={inputId}
        aria-invalid={error !== undefined}
        aria-describedby={error === undefined ? undefined : errorId}
        {...input}
      />
      {error !== undefined && (
        <p id={errorId} className="field__error">
          {error}
        </p>
      )}
    </div>
  )
}
