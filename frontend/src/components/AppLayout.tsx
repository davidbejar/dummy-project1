import { Outlet, useNavigate } from 'react-router'
import { useAuth } from '../features/auth/useAuth.ts'

/** Shell for signed-in pages: header with the user's name and logout, then the page. */
export function AppLayout() {
  const { state, logout } = useAuth()
  const navigate = useNavigate()

  async function signOut() {
    await logout().catch(() => {}) // the local session ends even if the request fails
    navigate('/', { replace: true })
  }

  return (
    <>
      <header className="app-header">
        <span className="brand">Dummy Project</span>
        {state.user && (
          <div className="user-menu">
            <span className="user-name" title={state.user.username}>{state.user.displayName}</span>
            <button type="button" onClick={signOut}>Log out</button>
          </div>
        )}
      </header>
      <Outlet />
    </>
  )
}
