import { Navigate, Outlet, useLocation } from 'react-router'
import { NotEnoughPermissions } from '../../components/NotEnoughPermissions.tsx'
import { screensOf } from './authContext.ts'
import { useAuth } from './useAuth.ts'

/**
 * Renders child routes only when the current role may open `screen`. Visitors who are not
 * signed in go to the login page; signed-in users are told they lack permission.
 */
export function RequireScreen({ screen }: { screen: string }) {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') return <p className="loading" role="status">Loading…</p>
  if (screensOf(state).includes(screen)) return <Outlet />
  if (state.status === 'anonymous') {
    // Remember where the visitor was going so login can send them back there.
    return <Navigate to="/" replace state={{ from: location.pathname }} />
  }
  return <NotEnoughPermissions />
}
