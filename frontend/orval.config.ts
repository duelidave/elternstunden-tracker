import { defineConfig } from 'orval'

// API-first (D-25): typed client and zod schemas are generated from the shared contract.
// Output goes to src/api/generated (gitignored); `npm run generate:api` rebuilds it.
const contract = '../api/openapi.yaml'

export default defineConfig({
  api: {
    input: { target: contract },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/endpoints',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      baseUrl: { getBaseUrlFromSpecification: true },
      clean: true,
      override: {
        mutator: { path: 'src/api/http.ts', name: 'apiFetch' },
        fetch: { includeHttpResponseReturnType: false },
      },
    },
  },
  apiZod: {
    input: { target: contract },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/zod',
      client: 'zod',
      fileExtension: '.zod.ts',
      clean: true,
    },
  },
})
