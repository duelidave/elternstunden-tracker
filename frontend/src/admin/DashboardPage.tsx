import { Link } from 'react-router'
import { errorText } from '../api/errors'
import { useGetAdminDashboard } from '../api/generated/endpoints/dashboard/dashboard'
import { useAssignUserToFamily } from '../api/generated/endpoints/users/users'
import type { AdminUserDetail } from '../api/generated/model'
import { texts } from '../i18n/de'
import { formatDate, formatDateTime } from '../time'
import { useInvalidateAll } from './invalidate'

/** Admin start page (7.6): entries to confirm, unassigned users with candidates, new automatic assignments. */
export function DashboardPage() {
  const dashboard = useGetAdminDashboard()
  if (dashboard.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (dashboard.isError) {
    return (
      <p className="alert" role="alert">
        {errorText(dashboard.error)}
      </p>
    )
  }
  const { openEntryCount, unassignedUsers, recentAutoAssignments } = dashboard.data
  return (
    <>
      <section className="card">
        <h2>
          {texts.admin.entries.title} <span className="count">{openEntryCount}</span>
        </h2>
        <p>{openEntryCount === 0 ? texts.admin.entries.widgetEmpty : texts.admin.entries.widgetCount(openEntryCount)}</p>
        {openEntryCount > 0 && (
          <Link className="button" to="/admin/entries">
            {texts.admin.entries.toList}
          </Link>
        )}
      </section>
      <section className="card">
        <h2>
          {texts.admin.dashboard.unassignedTitle} <span className="count">{unassignedUsers.length}</span>
        </h2>
        {unassignedUsers.length === 0 ? (
          <p className="text-muted">{texts.admin.dashboard.unassignedEmpty}</p>
        ) : (
          <ul className="stack">
            {unassignedUsers.map((detail) => (
              <UnassignedUser key={detail.user.id} detail={detail} />
            ))}
          </ul>
        )}
      </section>
      <section className="card">
        <h2>{texts.admin.dashboard.recentTitle}</h2>
        <p className="text-muted">{texts.admin.dashboard.recentHint}</p>
        {recentAutoAssignments.length === 0 ? (
          <p className="text-muted">{texts.admin.dashboard.recentEmpty}</p>
        ) : (
          <ul className="stack">
            {recentAutoAssignments.map((entry) => (
              <li key={`${entry.userId}-${entry.assignedAt}`} className="panel">
                <Link to={`/admin/users/${entry.userId}`}>
                  {texts.admin.dashboard.recentEntry(entry.displayName, entry.familyLabel)}
                </Link>
                <span className="block text-muted">{formatDateTime(entry.assignedAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

/** One unassigned user with the entered child data and one-click assignment to a candidate's family (F5). */
export function UnassignedUser({ detail }: { detail: AdminUserDetail }) {
  const assign = useAssignUserToFamily()
  const invalidate = useInvalidateAll()
  const { user, claim, candidates } = detail

  const assignTo = async (familyId: string) => {
    try {
      await assign.mutateAsync({ userId: user.id, data: { familyId } })
      await invalidate()
    } catch {
      // shown below
    }
  }

  return (
    <li className="panel">
      <Link to={`/admin/users/${user.id}`} className="font-semibold">
        {user.displayName}
      </Link>
      <span className="block text-muted break-all">{user.email}</span>
      {claim === undefined ? (
        <p className="text-muted">{texts.admin.dashboard.noClaim}</p>
      ) : (
        <p>
          {texts.pending.child(`${claim.firstName} ${claim.lastName}`, formatDate(claim.birthDate))}
          <span className="chip">{texts.admin.matchResult[claim.matchResult]}</span>
        </p>
      )}
      {claim !== undefined && (
        <>
          <h3>{texts.admin.dashboard.candidates}</h3>
          {candidates.length === 0 ? (
            <p className="text-muted">{texts.admin.dashboard.noCandidates}</p>
          ) : (
            <ul className="stack">
              {candidates.map((candidate) => (
                <li key={candidate.childId} className="candidate">
                  <span>
                    {candidate.firstName} {candidate.lastName}, {formatDate(candidate.birthDate)}
                  </span>
                  <button
                    type="button"
                    className="button button--secondary"
                    disabled={assign.isPending}
                    onClick={() => void assignTo(candidate.familyId)}
                  >
                    {texts.admin.dashboard.assignTo(candidate.familyLabel || texts.admin.families.noLabel)}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {assign.isError && (
        <p className="alert" role="alert">
          {errorText(assign.error)}
        </p>
      )}
    </li>
  )
}
