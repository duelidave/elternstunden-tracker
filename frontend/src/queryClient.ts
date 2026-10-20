import { QueryClient } from '@tanstack/react-query'

// Server state lives in TanStack Query (Lastenheft 13.2)
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: 1, refetchOnWindowFocus: false },
    },
  })
}
