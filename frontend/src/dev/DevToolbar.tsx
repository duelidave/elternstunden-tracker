import { useNavigate } from 'react-router'
import { useLogout } from '../api/generated/endpoints/auth/auth'
import { useDevLoginAs } from '../api/generated/endpoints/dev/dev'
import { DevLoginRequestAccount } from '../api/generated/model'
import { useRefreshSession, useSession } from '../api/session'

// Dev-only texts stay here, so none of them ends up in the production bundle
const texts = {
  label: 'Dev: Rolle wechseln',
  anonymous: 'Nicht angemeldet',
  logout: 'Abmelden',
  accounts: {
    FAMILY: 'Familie',
    PARTNER: 'Partner',
    UNASSIGNED: 'Unzugeordnet',
    ADMIN: 'Admin',
    MASTER: 'Master',
  } satisfies Record<DevLoginRequestAccount, string>,
}

/** Role switcher for manual tests in the local dev server (FS-DEV 7.12, D-26). */
export default function DevToolbar() {
  const session = useSession()
  const loginAs = useDevLoginAs()
  const logout = useLogout()
  const refreshSession = useRefreshSession()
  const navigate = useNavigate()
  const busy = loginAs.isPending || logout.isPending

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
    } finally {
      await refreshSession()
      await navigate('/')
    }
  }

  return (
    <aside
      data-dev-toolbar
      aria-label={texts.label}
      className="sticky bottom-0 border-t-2 border-dashed border-text bg-surface-muted px-4 py-2 text-sm"
    >
      <p className="my-1">
        <strong>{texts.label}</strong>
        {' · '}
        {session.me === undefined ? texts.anonymous : `${session.me.role}: ${session.me.email}`}
      </p>
      <div className="flex flex-wrap gap-2">
        {Object.values(DevLoginRequestAccount).map((account) => (
          <button
            key={account}
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => void run(() => loginAs.mutateAsync({ data: { account } }))}
          >
            {texts.accounts[account]}
          </button>
        ))}
        <button
          type="button"
          className="button button--secondary"
          disabled={busy || session.me === undefined}
          onClick={() => void run(() => logout.mutateAsync())}
        >
          {texts.logout}
        </button>
      </div>
    </aside>
  )
}
