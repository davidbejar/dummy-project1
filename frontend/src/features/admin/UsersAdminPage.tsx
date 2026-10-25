import { FormEvent, useEffect, useState } from 'react'
import { errorText, useApiAction } from '../../lib/useApiAction.ts'
import { AdminUser, NewUser, RoleOption, usersApi } from './api.ts'
import { BackToAdministration } from './BackToAdministration.tsx'

const EMPTY_USER: NewUser = { username: '', displayName: '', password: '', role: '' }

/** User administration: see, add and delete users. */
export function UsersAdminPage() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [roles, setRoles] = useState<RoleOption[]>([])
  const [draft, setDraft] = useState<NewUser>(EMPTY_USER)
  const { error, setError, run } = useApiAction()

  useEffect(() => {
    Promise.all([usersApi.list(), usersApi.roles()])
      .then(([users, roles]) => {
        setUsers(users)
        setRoles(roles)
      })
      .catch((e: unknown) => setError(errorText(e)))
  }, [setError])

  // Until a role is picked, new users get USER (or the first role there is).
  const role = draft.role || (roles.find((r) => r.code === 'USER') ?? roles[0])?.code || ''

  function add(e: FormEvent) {
    e.preventDefault()
    if (!draft.username.trim() || !draft.displayName.trim() || !draft.password || !role) {
      setError('Enter a username, display name, password and role')
      return
    }
    return run(async () => {
      const user = await usersApi.create({ ...draft, username: draft.username.trim(), displayName: draft.displayName.trim(), role })
      setUsers((us) => [...us, user].sort((a, b) => a.username.localeCompare(b.username)))
      setDraft(EMPTY_USER)
    })
  }

  function remove(user: AdminUser) {
    return run(async () => {
      await usersApi.remove(user.id)
      setUsers((us) => us.filter((u) => u.id !== user.id))
    })
  }

  const field = (name: keyof NewUser) => ({
    value: draft[name],
    onChange: (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [name]: e.target.value })),
  })

  return (
    <main className="admin">
      <BackToAdministration />
      <h1>User administration</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <form className="admin-form" onSubmit={add} noValidate>
        <label>Username<input name="username" autoComplete="off" maxLength={50} {...field('username')} /></label>
        <label>Display name<input name="displayName" autoComplete="off" maxLength={100} {...field('displayName')} /></label>
        <label>Password<input name="password" type="password" autoComplete="new-password" maxLength={72} {...field('password')} /></label>
        <label>
          Role
          <select name="role" value={role} onChange={(e) => setDraft((d) => ({ ...d, role: e.target.value }))}>
            {roles.map((r) => <option key={r.code} value={r.code}>{r.displayName}</option>)}
          </select>
        </label>
        <button type="submit">Add user</button>
      </form>
      <table className="admin-table">
        <thead>
          <tr><th>Username</th><th>Name</th><th>Role</th><th><span className="visually-hidden">Actions</span></th></tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.username}</td>
              <td>{u.displayName}</td>
              <td>{u.roleDisplayName}</td>
              <td>
                <button
                  type="button"
                  onClick={() => remove(u)}
                  disabled={!u.deletable}
                  title={u.deletable ? undefined : 'This user cannot be deleted'}
                  aria-label={`Delete ${u.username}`}
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  )
}
