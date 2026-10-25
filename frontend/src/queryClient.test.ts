import { describe, expect, it, vi } from 'vitest'
import { ApiProblem } from './api/http'
import { createQueryClient } from './queryClient'

describe('createQueryClient', () => {
  it('reloads the account when any request answers 401 (session timed out)', async () => {
    const client = createQueryClient()
    const reset = vi.spyOn(client, 'resetQueries')

    await expect(
      client.fetchQuery({
        queryKey: ['/api/v1/admin/entries'],
        queryFn: () => Promise.reject(new ApiProblem(401, { code: 'UNAUTHORIZED' })),
        retry: false,
      }),
    ).rejects.toBeInstanceOf(ApiProblem)

    expect(reset).toHaveBeenCalledWith({ queryKey: ['/api/v1/me'] })
  })

  it('does not loop on the account query itself', async () => {
    const client = createQueryClient()
    const reset = vi.spyOn(client, 'resetQueries')

    await expect(
      client.fetchQuery({
        queryKey: ['/api/v1/me'],
        queryFn: () => Promise.reject(new ApiProblem(401, { code: 'UNAUTHORIZED' })),
        retry: false,
      }),
    ).rejects.toBeInstanceOf(ApiProblem)

    expect(reset).not.toHaveBeenCalled()
  })
})
