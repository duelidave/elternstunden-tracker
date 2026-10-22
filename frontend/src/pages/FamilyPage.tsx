import { Link, Navigate } from 'react-router'
import { errorText } from '../api/errors'
import { useGetFamilyOverview } from '../api/generated/endpoints/my-family/my-family'
import { useSession } from '../api/session'
import { texts } from '../i18n/de'
import { PartnerHint } from './FamilyHome'

/** The own family (13.1 "/family"): members, children's first names and the partner hint (7.3 rules 6 and 7). */
export function FamilyPage() {
  const session = useSession()
  const overview = useGetFamilyOverview(undefined, { query: { enabled: session.me?.assignedToFamily === true } })
  if (session.isLoading) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (session.me === undefined) {
    return <Navigate to="/login" replace />
  }
  if (!session.me.assignedToFamily) {
    return <Navigate to={session.me.role === 'FAMILY_MEMBER' ? '/pending' : '/'} replace />
  }
  if (overview.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (overview.isError) {
    return (
      <p className="alert" role="alert">
        {errorText(overview.error)}
      </p>
    )
  }
  const { members, children } = overview.data
  return (
    <>
      <section className="card">
        <h2>{texts.family.familyTitle}</h2>
        <h3>{texts.family.children}</h3>
        <ul className="stack">
          {children.map((child, index) => (
            <li key={`${child.firstName}-${String(index)}`} className="panel">
              {child.firstName}
            </li>
          ))}
        </ul>
        <h3>{texts.family.members}</h3>
        <ul className="stack">
          {members.map((member, index) => (
            <li key={`${member.displayName}-${String(index)}`} className="panel">
              {member.displayName} {member.isCurrentUser && <span className="text-muted">{texts.family.you}</span>}
            </li>
          ))}
        </ul>
      </section>
      <PartnerHint overview={overview.data} />
      <p className="mt-4 text-center">
        <Link className="inline-flex min-h-tap items-center" to="/">
          {texts.family.back}
        </Link>
      </p>
    </>
  )
}
