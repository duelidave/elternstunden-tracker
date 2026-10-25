import { Link, Navigate, useNavigate } from 'react-router'
import { errorText } from '../api/errors'
import { useLogout } from '../api/generated/endpoints/auth/auth'
import { useSessionChanged, useSession } from '../api/session'
import { texts } from '../i18n/de'
import { FamilyHome } from './FamilyHome'

/**
 * Start page after login: the family overview for everyone assigned to a family (7.3), otherwise the way to the admin
 * area. Unassigned family members wait on /pending (7.1.7).
 */
export function HomePage() {
  const session = useSession()
  const logout = useLogout()
  const refreshSession = useSessionChanged()
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
  if (session.me.assignedToFamily) {
    return (
      <>
        {isAdmin && (
          <p className="mt-0">
            <Link className="button button--secondary w-full" to="/admin">
              {texts.home.toAdmin}
            </Link>
          </p>
        )}
        <FamilyHome me={session.me} />
      </>
    )
  }

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
      <p>{session.me.role === 'MASTER_ADMIN' ? texts.home.master : texts.home.admin}</p>
      <p>
        <Link className="button w-full" to="/admin">
          {texts.home.toAdmin}
        </Link>
      </p>
      <p>
        <Link className="button button--secondary w-full" to="/profile">
          {texts.home.toProfile}
        </Link>
      </p>
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
