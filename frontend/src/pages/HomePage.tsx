import { Link, Navigate, useNavigate } from 'react-router'
import { errorText } from '../api/errors'
import { useLogout } from '../api/generated/endpoints/auth/auth'
import type { Me } from '../api/generated/model'
import { useRefreshSession, useSession } from '../api/session'
import { texts } from '../i18n/de'

function welcomeText(me: Me): string {
  switch (me.role) {
    case 'MASTER_ADMIN':
      return texts.home.master
    case 'ADMIN':
      return texts.home.admin
    case 'FAMILY_MEMBER':
      return texts.home.family
  }
}

/** Start page after login. The family overview follows with M4; unassigned members wait on /pending (7.1.7). */
export function HomePage() {
  const session = useSession()
  const logout = useLogout()
  const refreshSession = useRefreshSession()
  const navigate = useNavigate()

  if (session.isLoading) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (session.error !== undefined) {
    return (
      <p className="alert" role="alert">
        {errorText(session.error)}
      </p>
    )
  }
  if (session.me === undefined) {
    return <Navigate to="/login" replace />
  }
  if (session.me.role === 'FAMILY_MEMBER' && !session.me.assignedToFamily) {
    return <Navigate to="/pending" replace />
  }
  const isAdmin = session.me.role !== 'FAMILY_MEMBER'

  const signOut = async () => {
    try {
      await logout.mutateAsync()
    } catch {
      // the session is gone either way
    }
    await refreshSession()
    await navigate('/login', { replace: true })
  }

  return (
    <section className="card">
      <h2>{texts.home.greeting(session.me.displayName)}</h2>
      <p>{welcomeText(session.me)}</p>
      {isAdmin && (
        <p>
          <Link className="button w-full" to="/admin">
            {texts.home.toAdmin}
          </Link>
        </p>
      )}
      <button
        type="button"
        className="button button--secondary"
        disabled={logout.isPending}
        onClick={() => void signOut()}
      >
        {texts.common.logout}
      </button>
    </section>
  )
}
