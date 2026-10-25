import { Link } from 'react-router'
import { SCREEN_PATHS } from '../../app/screens.ts'

export function BackToAdministration() {
  return <Link className="back-link" to={SCREEN_PATHS.ADMINISTRATION}>← Administration</Link>
}
