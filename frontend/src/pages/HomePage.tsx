import { Navigate, useNavigate } from 'react-router'
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
      return me.assignedToFamily ? texts.home.family : texts.home.pending
  }
}

/** Start page after login. The real screens per role follow from M2 on (family overview, admin area). */
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
