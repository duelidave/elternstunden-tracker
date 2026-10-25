import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { getGetMeQueryKey, useGetMe } from './generated/endpoints/me/me'
import type { Me } from './generated/model'
import { ApiProblem } from './http'

export interface Session {
  me: Me | undefined
  isLoading: boolean
  /** Any error other than "not signed in", e.g. the server is not reachable. */
  error: unknown
}

/** The signed-in account; a 401 simply means "not signed in". */
export function useSession(): Session {
  const query = useGetMe({ query: { retry: false, staleTime: 30_000 } })
  const unauthorized = query.error instanceof ApiProblem && query.error.status === 401
  return {
    me: unauthorized ? undefined : query.data,
    isLoading: query.isPending && !unauthorized,
    error: unauthorized ? undefined : (query.error ?? undefined),
  }
}

/** Reloads the account, e.g. after a change of the name or a linked provider. */
export function useRefreshSession(): () => Promise<void> {
  const client = useQueryClient()
  return useCallback(async () => {
    await client.resetQueries({ queryKey: getGetMeQueryKey() })
  }, [client])
}

/**
 * After a login, logout or role switch: nothing of the previous account may stay in the cache (family data, admin
 * lists), so every query starts again.
 */
export function useSessionChanged(): () => Promise<void> {
  const client = useQueryClient()
  return useCallback(async () => {
    await client.resetQueries()
  }, [client])
}
