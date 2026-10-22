import { useCallback } from 'react'
import { useSearchParams } from 'react-router'

/**
 * Filters and search of admin lists live in the URL (UI-11): shareable, and the back button works. Changing a filter
 * starts again at the first page.
 */
export function useListParams() {
  const [params, setParams] = useSearchParams()
  const set = useCallback(
    (name: string, value: string) => {
      setParams((current) => {
        const next = new URLSearchParams(current)
        if (value === '') {
          next.delete(name)
        } else {
          next.set(name, value)
        }
        if (name !== 'page') {
          next.delete('page')
        }
        return next
      })
    },
    [setParams],
  )
  return {
    get: (name: string) => params.get(name) ?? '',
    page: Number(params.get('page') ?? '0'),
    set,
  }
}
