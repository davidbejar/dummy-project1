import { FormEvent, useEffect, useState } from 'react'
import { errorText, useApiAction } from '../../lib/useApiAction.ts'
import { useAuth } from '../auth/useAuth.ts'
import { AdminRole, rolesApi, ScreenInfo } from './api.ts'
import { BackToAdministration } from './BackToAdministration.tsx'

/** Role administration: see, add and delete roles, and choose which screens each role can open. */
export function RolesAdminPage() {
  const { state, refresh } = useAuth()
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [screens, setScreens] = useState<ScreenInfo[]>([])
  const [code, setCode] = useState('')
  const [displayName, setDisplayName] = useState('')
  const { error, setError, run } = useApiAction()

  useEffect(() => {
    Promise.all([rolesApi.list(), rolesApi.screens()])
      .then(([roles, screens]) => {
        setRoles(roles)
        setScreens(screens)
      })
      .catch((e: unknown) => setError(errorText(e)))
  }, [setError])

  function add(e: FormEvent) {
    e.preventDefault()
    if (!code.trim() || !displayName.trim()) {
      setError('Enter a code and a display name')
      return
    }
    return run(async () => {
      const role = await rolesApi.create(code.trim(), displayName.trim())
      setRoles((rs) => [...rs, role].sort((a, b) => a.code.localeCompare(b.code)))
      setCode('')
      setDisplayName('')
    })
  }

  function remove(role: AdminRole) {
    return run(async () => {
      await rolesApi.remove(role.code)
      setRoles((rs) => rs.filter((r) => r.code !== role.code))
    })
  }

  // The backend also gives the screens above a screen, and takes away the screens below it.
  function toggle(role: AdminRole, screen: string) {
    return run(async () => {
      const assigned = role.screens.includes(screen)
      const updated = await (assigned ? rolesApi.unassign(role.code, screen) : rolesApi.assign(role.code, screen))
      setRoles((rs) => rs.map((r) => (r.code === updated.code ? updated : r)))
      // The signed-in user's own screens changed, so the header and route guards must know.
      if (state.status === 'authenticated' && state.user.role === role.code) await refresh()
    })
  }

  return (
    <main className="admin admin-wide">
      <BackToAdministration />
      <h1>Role administration</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <form className="admin-form" onSubmit={add} noValidate>
        <label>Code<input name="code" autoComplete="off" maxLength={20} value={code} onChange={(e) => setCode(e.target.value)} /></label>
        <label>Display name<input name="displayName" autoComplete="off" maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} /></label>
        <button type="submit">Add role</button>
      </form>
      <div className="table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Role</th>
              {screens.map((s) => (
                <th key={s.code}>
                  {s.displayName}
                  {s.parentCode && (
                    <>
                      {' '}
                      <small className="screen-parent">
                        in {screens.find((p) => p.code === s.parentCode)?.displayName ?? s.parentCode}
                      </small>
                    </>
                  )}
                </th>
              ))}
              <th><span className="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {roles.map((r) => (
              <tr key={r.code}>
                <td>{r.displayName} <code>{r.code}</code></td>
                {screens.map((s) => (
                  <td key={s.code} className="center">
                    <input
                      type="checkbox"
                      checked={r.screens.includes(s.code)}
                      disabled={r.lockedScreens.includes(s.code)}
                      onChange={() => toggle(r, s.code)}
                      aria-label={`${r.displayName} can open ${s.displayName}`}
                    />
                  </td>
                ))}
                <td>
                  <button
                    type="button"
                    onClick={() => remove(r)}
                    disabled={!r.deletable}
                    title={r.deletable ? undefined : 'This role cannot be deleted'}
                    aria-label={`Delete role ${r.code}`}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  )
}
