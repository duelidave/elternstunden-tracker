import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getGetFamilyOverviewQueryKey, useGetFamilyOverview } from '../api/generated/endpoints/my-family/my-family'

/** The overview of the school year in the URL (?year=…), shareable; without it the current one (7.3 rule 2). */
export function useFamilyOverview() {
  const [params] = useSearchParams()
  const year = params.get('year') ?? undefined
  return useGetFamilyOverview(year === undefined ? undefined : { schoolYearId: year })
}

/** After a change every school year's overview may be stale. */
export function useRefreshFamily(): () => Promise<void> {
  const client = useQueryClient()
  return useCallback(async () => {
    await client.invalidateQueries({ queryKey: getGetFamilyOverviewQueryKey() })
  }, [client])
}

const HINT_KEY = 'elternstunden.partnerHintDismissed'

/** The partner hint can be hidden for good, e.g. by single parents (7.3 rule 6, [PROPOSED] browser storage). */
export function usePartnerHintDismissed(): [boolean, () => void] {
  const [dismissed, setDismissed] = useState(() => {
    try {
      return localStorage.getItem(HINT_KEY) === 'true'
    } catch {
      return false
    }
  })
  const dismiss = useCallback(() => {
    try {
      localStorage.setItem(HINT_KEY, 'true')
    } catch {
      // still hidden for this visit
    }
    setDismissed(true)
  }, [])
  return [dismissed, dismiss]
}
