import { Link } from 'react-router'
import { Screen, SCREEN_PATHS, ScreenCode } from '../../app/screens.ts'
import { screensOf } from '../auth/authContext.ts'
import { useAuth } from '../auth/useAuth.ts'

const OPTIONS: { screen: ScreenCode; name: string; description: string }[] = [
  { screen: Screen.USER_ADMIN, name: 'User administration', description: 'See, add and delete users.' },
  {
    screen: Screen.ROLE_ADMIN,
    name: 'Role administration',
    description: 'See, add and delete roles, and choose which screens each role can open.',
  },
]

/** The administration menu. Each option is a screen of its own, shown only to roles that may open it. */
export function AdministrationPage() {
  const { state } = useAuth()
  const options = OPTIONS.filter((option) => screensOf(state).includes(option.screen))

  return (
    <main className="admin">
      <h1>Administration</h1>
      <nav aria-label="Administration">
        {options.length === 0 && <p>There are no administration screens you may open.</p>}
        <ul className="admin-menu">
          {options.map((option) => (
            <li key={option.screen}>
              <Link to={SCREEN_PATHS[option.screen]}>{option.name}</Link>
              <span>{option.description}</span>
            </li>
          ))}
        </ul>
      </nav>
    </main>
  )
}
