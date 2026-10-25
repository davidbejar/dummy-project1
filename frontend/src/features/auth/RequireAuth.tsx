import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './useAuth.ts'

/** Renders child routes only for a signed-in user; everyone else goes to the login page. */
export function RequireAuth() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') return <p className="loading" role="status">Loading…</p>
  if (state.status === 'anonymous') {
    // Remember where the user was going so login can send them back there.
    return <Navigate to="/" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
