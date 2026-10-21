import { Navigate, type RouteObject } from 'react-router'
import { Layout } from './Layout'
import { HomePage } from './pages/HomePage'
import { LegalPage } from './pages/LegalPage'

// Screens of 13.1 that exist so far. The login screens (captcha, forms) load on demand (13.2, NFR-02).
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', lazy: async () => ({ Component: (await import('./pages/LoginPage')).LoginPage }) },
      { path: 'login/link', lazy: async () => ({ Component: (await import('./pages/LinkPage')).LinkPage }) },
      {
        path: 'login/master',
        lazy: async () => ({ Component: (await import('./pages/MasterLoginPage')).MasterLoginPage }),
      },
      { path: 'register', lazy: async () => ({ Component: (await import('./pages/RegisterPage')).RegisterPage }) },
      { path: 'legal/privacy', element: <LegalPage kind="privacy" /> },
      { path: 'legal/imprint', element: <LegalPage kind="imprint" /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]
