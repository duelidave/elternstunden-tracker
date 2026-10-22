import { useEffect, useId, useState } from 'react'
import { texts } from '../i18n/de'

interface SearchFieldProps {
  value: string
  onSearch: (query: string) => void
}

/** Search above admin lists (UI-11): waits 250 ms after typing and only searches from 2 characters on. */
export function SearchField({ value, onSearch }: SearchFieldProps) {
  const id = useId()
  const [text, setText] = useState(value)

  useEffect(() => {
    const trimmed = text.trim()
    const query = trimmed.length < 2 ? '' : trimmed
    if (query === value) {
      return
    }
    const timer = window.setTimeout(() => {
      onSearch(query)
    }, 250)
    return () => {
      window.clearTimeout(timer)
    }
  }, [text, value, onSearch])

  return (
    <div className="field">
      <label htmlFor={id}>{texts.admin.search}</label>
      <input
        id={id}
        type="search"
        value={text}
        placeholder={texts.admin.searchHint}
        onChange={(event) => {
          setText(event.target.value)
        }}
      />
    </div>
  )
}
