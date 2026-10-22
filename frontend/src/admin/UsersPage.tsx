import { useId, useState } from 'react'
import { Link, useParams } from 'react-router'
import { errorText } from '../api/errors'
import {
  useAnonymizeUser,
  useAssignUserToFamily,
  useDisableUser,
  useEnableUser,
  useGetUser,
  useListUsers,
  useRemoveUserFromFamily,
  useSetUserRole,
  useTerminateUserSessions,
} from '../api/generated/endpoints/users/users'
import { type AdminUser, type AdminUserDetail, RoleChangeRole, UserStatus } from '../api/generated/model'
import { type Column, DataTable } from '../components/DataTable'
import { Pager } from '../components/Pager'
import { SearchField } from '../components/SearchField'
import { texts } from '../i18n/de'
import { formatDateTime } from '../time'
import { UnassignedUser } from './DashboardPage'
import { FamilySelect } from './FamilySelect'
import { useInvalidateAll } from './invalidate'
import { useListParams } from './params'

const columns: Column<AdminUser>[] = [
  {
    header: texts.admin.users.name,
    cell: (user) => <Link to={`/admin/users/${user.id}`}>{user.displayName}</Link>,
  },
  { header: texts.admin.users.email, cell: (user) => <span className="break-all">{user.email}</span> },
  { header: texts.admin.users.role, cell: (user) => texts.admin.users.roles[user.role] },
  { header: texts.admin.users.status, cell: (user) => texts.admin.users.statuses[user.status] },
  {
    header: texts.admin.users.family,
    cell: (user) =>
      user.familyId === undefined ? (
        texts.admin.none
      ) : (
        <Link to={`/admin/families/${user.familyId}`}>{user.familyLabel || texts.admin.families.noLabel}</Link>
      ),
  },
]

/** Users with search and filters (FS-ADM 7.7, UI-11). */
export function UsersPage() {
  const params = useListParams()
  const statusId = useId()
  const status = params.get('status')
  const users = useListUsers({
    q: params.get('q') || undefined,
    status: Object.values(UserStatus).find((value) => value === status),
    unassigned: params.get('unassigned') === '1' || undefined,
    page: params.page,
  })
  return (
    <section className="card">
      <h2>{texts.admin.users.title}</h2>
      <div className="filters">
        <SearchField
          value={params.get('q')}
          onSearch={(query) => {
            params.set('q', query)
          }}
        />
        <div className="field">
          <label htmlFor={statusId}>{texts.admin.users.status}</label>
          <select
            id={statusId}
            value={status}
            onChange={(event) => {
              params.set('status', event.target.value)
            }}
          >
            <option value="">{texts.admin.users.allStatuses}</option>
            {Object.values(UserStatus).map((value) => (
              <option key={value} value={value}>
                {texts.admin.users.statuses[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="checkbox">
          <input
            id={`${statusId}-unassigned`}
            type="checkbox"
            checked={params.get('unassigned') === '1'}
            onChange={(event) => {
              params.set('unassigned', event.target.checked ? '1' : '')
            }}
          />
          <label htmlFor={`${statusId}-unassigned`}>{texts.admin.users.onlyUnassigned}</label>
        </div>
      </div>
      {users.isError && (
        <p className="alert" role="alert">
          {errorText(users.error)}
        </p>
      )}
      {users.data && (
        <>
          <DataTable
            caption={texts.admin.users.title}
            columns={columns}
            rows={users.data.content}
            rowKey={(user) => user.id}
            empty={texts.admin.empty}
          />
          <Pager
            page={users.data.page}
            totalPages={users.data.totalPages}
            totalElements={users.data.totalElements}
            onPage={(page) => {
              params.set('page', String(page))
            }}
          />
        </>
      )}
    </section>
  )
}

/** One user: claim and candidates, family, role and account actions (F5, D-13, DP-05). */
export function UserPage() {
  const { userId = '' } = useParams()
  const user = useGetUser(userId)
  if (user.isPending) {
    return <p role="status">{texts.common.loading}</p>
  }
  if (user.isError) {
    return (
      <p className="alert" role="alert">
        {errorText(user.error)}
      </p>
    )
  }
  return <UserDetailView detail={user.data} />
}

function UserDetailView({ detail }: { detail: AdminUserDetail }) {
  const { user } = detail
  return (
    <section className="card">
      <h2>{user.displayName}</h2>
      <dl className="facts">
        <dt>{texts.admin.users.email}</dt>
        <dd className="break-all">{user.email}</dd>
        <dt>{texts.admin.users.role}</dt>
        <dd>{texts.admin.users.roles[user.role]}</dd>
        <dt>{texts.admin.users.status}</dt>
        <dd>{texts.admin.users.statuses[user.status]}</dd>
        <dt>{texts.admin.users.family}</dt>
        <dd>
          {user.familyId === undefined ? (
            texts.admin.none
          ) : (
            <Link to={`/admin/families/${user.familyId}`}>{user.familyLabel || texts.admin.families.noLabel}</Link>
          )}
        </dd>
        <dt>{texts.admin.users.lastLogin}</dt>
        <dd>{user.lastLoginAt === undefined ? texts.admin.none : formatDateTime(user.lastLoginAt)}</dd>
      </dl>
      {user.managedByConfig ? (
        <p className="notice">{texts.admin.users.managed}</p>
      ) : (
        <UserActions detail={detail} />
      )}
      <p className="card__footer">
        <Link to="/admin/users">{texts.admin.back}</Link>
      </p>
    </section>
  )
}

function UserActions({ detail }: { detail: AdminUserDetail }) {
  const { user } = detail
  const roleId = useId()
  const [familyId, setFamilyId] = useState('')
  const [role, setRole] = useState<RoleChangeRole>(user.role === 'ADMIN' ? 'ADMIN' : 'FAMILY_MEMBER')
  const invalidate = useInvalidateAll()
  const assign = useAssignUserToFamily()
  const remove = useRemoveUserFromFamily()
  const setUserRole = useSetUserRole()
  const disable = useDisableUser()
  const enable = useEnableUser()
  const terminate = useTerminateUserSessions()
  const anonymize = useAnonymizeUser()
  const mutations = [assign, remove, setUserRole, disable, enable, terminate, anonymize]
  const busy = mutations.some((mutation) => mutation.isPending)
  const error = mutations.find((mutation) => mutation.isError)?.error

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action()
      await invalidate()
    } catch {
      // shown below
    }
  }
  const userId = user.id

  return (
    <>
      {user.familyId === undefined && user.role === 'FAMILY_MEMBER' && (
        <>
          <h3 className="mt-6">{texts.admin.users.claimTitle}</h3>
          <ul className="stack">
            <UnassignedUser detail={detail} />
          </ul>
        </>
      )}
      <h3 className="mt-6">{texts.admin.users.assignTitle}</h3>
      <FamilySelect
        label={texts.admin.users.chooseFamily}
        value={familyId}
        onChange={(event) => {
          setFamilyId(event.target.value)
        }}
      />
      <div className="actions mt-0">
        <button
          type="button"
          className="button"
          disabled={busy || familyId === ''}
          onClick={() => void run(() => assign.mutateAsync({ userId, data: { familyId } }))}
        >
          {texts.admin.users.assign}
        </button>
        {user.familyId !== undefined && (
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => void run(() => remove.mutateAsync({ userId }))}
          >
            {texts.admin.users.removeFamily}
          </button>
        )}
      </div>
      <h3 className="mt-6">{texts.admin.users.roleTitle}</h3>
      <div className="field">
        <label htmlFor={roleId}>{texts.admin.users.role}</label>
        <select
          id={roleId}
          value={role}
          onChange={(event) => {
            setRole(event.target.value === 'ADMIN' ? 'ADMIN' : 'FAMILY_MEMBER')
          }}
        >
          {Object.values(RoleChangeRole).map((value) => (
            <option key={value} value={value}>
              {texts.admin.users.roles[value]}
            </option>
          ))}
        </select>
      </div>
      <button
        type="button"
        className="button button--secondary"
        disabled={busy || role === user.role}
        onClick={() => void run(() => setUserRole.mutateAsync({ userId, data: { role } }))}
      >
        {texts.admin.save}
      </button>
      <h3 className="mt-6">{texts.admin.users.accountTitle}</h3>
      <div className="actions mt-0">
        {user.status === 'DISABLED' ? (
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => void run(() => enable.mutateAsync({ userId }))}
          >
            {texts.admin.users.enable}
          </button>
        ) : (
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => {
              if (window.confirm(texts.admin.users.confirmDisable)) {
                void run(() => disable.mutateAsync({ userId }))
              }
            }}
          >
            {texts.admin.users.disable}
          </button>
        )}
        <button
          type="button"
          className="button button--secondary"
          disabled={busy}
          onClick={() => void run(() => terminate.mutateAsync({ userId }))}
        >
          {texts.admin.users.terminateSessions}
        </button>
        <button
          type="button"
          className="button button--danger"
          disabled={busy}
          onClick={() => {
            if (window.confirm(texts.admin.users.confirmAnonymize)) {
              void run(() => anonymize.mutateAsync({ userId }))
            }
          }}
        >
          {texts.admin.users.anonymize}
        </button>
      </div>
      {terminate.isSuccess && (
        <p className="notice" role="status">
          {texts.admin.users.sessionsTerminated}
        </p>
      )}
      {error !== undefined && error !== null && (
        <p className="alert" role="alert">
          {errorText(error)}
        </p>
      )}
    </>
  )
}
