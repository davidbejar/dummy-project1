import { NavLink, useNavigate } from 'react-router'
import { Screen, SCREEN_PATHS } from '../app/screens.ts'
import { screensOf } from '../features/auth/authContext.ts'
import { useAuth } from '../features/auth/useAuth.ts'
import { ThemeToggle } from './ThemeToggle.tsx'

// Screens linked from the header, when the current role may open them.
const NAV_LINKS = [
  { screen: Screen.TODO, label: 'Todos' },
  { screen: Screen.ADMINISTRATION, label: 'Administration' },
]

/** Header on every screen: navigation, the theme toggle, and who is signed in with a logout button. */
export function AppHeader() {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  const screens = screensOf(state)

  async function signOut() {
    await logout().catch(() => {}) // the local session ends even if the request fails
    navigate('/', { replace: true })
  }

  return (
    <header className="app-header">
      <nav className="app-nav">
        <span className="brand">Dummy Project</span>
        {NAV_LINKS.filter((link) => screens.includes(link.screen)).map((link) => (
          <NavLink key={link.screen} to={SCREEN_PATHS[link.screen]}>{link.label}</NavLink>
        ))}
      </nav>
      <div className="header-actions">
        <ThemeToggle />
        {state.status === 'authenticated' && (
          <div className="user-menu">
            <span className="user-name" title={state.user.username}>{state.user.displayName}</span>
            <button type="button" onClick={signOut}>Log out</button>
          </div>
        )}
        {state.status === 'anonymous' && (
          <div className="user-menu">
            <span className="user-name">{state.visitor.roleDisplayName}</span>
          </div>
        )}
      </div>
    </header>
  )
}
