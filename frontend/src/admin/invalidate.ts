import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'

/** After a change in the admin area all cached lists may be stale; they are small, so everything reloads. */
export function useInvalidateAll(): () => Promise<void> {
  const client = useQueryClient()
  return useCallback(async () => {
    await client.invalidateQueries()
  }, [client])
}
