import { NavLink, Navigate, Outlet } from 'react-router'
import { useListEntries } from '../api/generated/endpoints/entries/entries'
import { useSession } from '../api/session'
import { texts } from '../i18n/de'
import { HintBar } from './HintBar'

const links = [
  { to: '/admin', label: texts.admin.nav.dashboard, end: true },
  { to: '/admin/entries', label: texts.admin.nav.entries, end: false },
  { to: '/admin/children', label: texts.admin.nav.children, end: false },
  { to: '/admin/families', label: texts.admin.nav.families, end: false },
  { to: '/admin/users', label: texts.admin.nav.users, end: false },
  { to: '/admin/school-years', label: texts.admin.nav.schoolYears, end: false },
] as const

/** Admin area (13.1): only for ADMIN and MASTER_ADMIN; the server checks every call anyway (SEC-01). */
export function AdminLayout() {
  const session = useSession()
  const isAdmin = session.me !== undefined && session.me.role !== 'FAMILY_MEMBER'
  // Badge with the open entries in the navigation (7.5 point 6)
  const open = useListEntries({ status: 'OPEN', size: 1 }, { query: { enabled: isAdmin } })
  const openCount = open.data?.totalElements ?? 0
  if (session.isLoading) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (session.me === undefined) {
    return <Navigate to="/login" replace />
  }
  if (session.me.role === 'FAMILY_MEMBER') {
    return <Navigate to="/" replace />
  }
  return (
    <div className="flex flex-col gap-4">
      <HintBar />
      <nav aria-label={texts.admin.nav.label} className="admin-nav">
        {links.map((link) => (
          <NavLink key={link.to} to={link.to} end={link.end} className="admin-nav__link">
            {link.label}
            {link.to === '/admin/entries' && openCount > 0 && (
              <>
                <span className="count" aria-hidden="true">
                  {openCount}
                </span>
                <span className="sr-only">{texts.admin.nav.openCount(openCount)}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  )
}
