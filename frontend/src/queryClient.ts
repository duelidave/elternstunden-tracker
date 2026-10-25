import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiProblem } from './api/http'
import { getGetMeQueryKey } from './api/generated/endpoints/me/me'

/**
 * Server state lives in TanStack Query (Lastenheft 13.2). A 401 anywhere (e.g. the session timed out) reloads the
 * account, so the route guards send the user to the login instead of showing a signed-in page that no longer works.
 */
export function createQueryClient(): QueryClient {
  const onError = (error: unknown) => {
    if (error instanceof ApiProblem && error.status === 401) {
      void client.resetQueries({ queryKey: getGetMeQueryKey() })
    }
  }
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        // The account query itself answers 401 when nobody is signed in; that is no news
        if (query.queryKey[0] !== getGetMeQueryKey()[0]) {
          onError(error)
        }
      },
    }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: { retry: 1, refetchOnWindowFocus: false },
    },
  })
  return client
}
