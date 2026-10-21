import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import './index.css'
import { createQueryClient } from './queryClient'
import { routes } from './routes'

const root = document.getElementById('root')
if (root === null) {
  throw new Error('Root element #root is missing')
}

const router = createBrowserRouter(routes)

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={createQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
