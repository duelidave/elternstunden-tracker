import { Navigate, type RouteObject } from 'react-router'
import { Layout } from './Layout'
import { HomePage } from './pages/HomePage'
import { LegalPage } from './pages/LegalPage'

const admin = () => import('./admin')

// Screens of 13.1 that exist so far. Login screens and the admin area load on demand (13.2, NFR-02).
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        element: <HomePage />,
        children: [
          { index: true },
          { path: 'entries/new', lazy: async () => ({ Component: (await import('./pages/EntrySheet')).EntrySheet }) },
          {
            path: 'entries/:entryId',
            lazy: async () => ({ Component: (await import('./pages/EntrySheet')).EntrySheet }),
          },
        ],
      },
      { path: 'family', lazy: async () => ({ Component: (await import('./pages/FamilyPage')).FamilyPage }) },
      { path: 'login', lazy: async () => ({ Component: (await import('./pages/LoginPage')).LoginPage }) },
      { path: 'login/link', lazy: async () => ({ Component: (await import('./pages/LinkPage')).LinkPage }) },
      {
        path: 'login/master',
        lazy: async () => ({ Component: (await import('./pages/MasterLoginPage')).MasterLoginPage }),
      },
      { path: 'register', lazy: async () => ({ Component: (await import('./pages/RegisterPage')).RegisterPage }) },
      { path: 'pending', lazy: async () => ({ Component: (await import('./pages/PendingPage')).PendingPage }) },
      {
        path: 'register/claim',
        lazy: async () => ({ Component: (await import('./pages/OidcClaimPage')).OidcClaimPage }),
      },
      { path: 'login/mfa', lazy: async () => ({ Component: (await import('./pages/MfaPage')).MfaPage }) },
      { path: 'profile', lazy: async () => ({ Component: (await import('./pages/ProfilePage')).ProfilePage }) },
      {
        path: 'admin',
        lazy: async () => ({ Component: (await admin()).AdminLayout }),
        children: [
          { index: true, lazy: async () => ({ Component: (await admin()).DashboardPage }) },
          { path: 'entries', lazy: async () => ({ Component: (await admin()).EntriesPage }) },
          { path: 'children', lazy: async () => ({ Component: (await admin()).ChildrenPage }) },
          { path: 'children/new', lazy: async () => ({ Component: (await admin()).ChildFormPage }) },
          { path: 'children/import', lazy: async () => ({ Component: (await admin()).ImportPage }) },
          { path: 'children/:childId', lazy: async () => ({ Component: (await admin()).ChildFormPage }) },
          { path: 'families', lazy: async () => ({ Component: (await admin()).FamiliesPage }) },
          { path: 'families/:familyId', lazy: async () => ({ Component: (await admin()).FamilyPage }) },
          { path: 'users', lazy: async () => ({ Component: (await admin()).UsersPage }) },
          { path: 'users/:userId', lazy: async () => ({ Component: (await admin()).UserPage }) },
          { path: 'school-years', lazy: async () => ({ Component: (await admin()).SchoolYearsPage }) },
        ],
      },
      { path: 'legal/privacy', element: <LegalPage kind="privacy" /> },
      { path: 'legal/imprint', element: <LegalPage kind="imprint" /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]
