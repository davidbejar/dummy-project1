import { FormEvent, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { canOpenPath, homePath } from '../../app/screens.ts'
import { CurrentUser } from './api.ts'
import { useAuth } from './useAuth.ts'

export function LoginPage() {
  const { state, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Where the user was going before being sent here, if they may open it; otherwise their first screen.
  const from = (location.state as { from?: string } | null)?.from
  const target = (user: CurrentUser) => (from && canOpenPath(from, user.screens) ? from : homePath(user.screens))

  if (state.status === 'loading') return <p className="loading" role="status">Loading…</p>
  if (state.status === 'authenticated') return <Navigate to={target(state.user)} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username and password')
      return
    }
    setSubmitting(true)
    try {
      const user = await login(username.trim(), password)
      navigate(target(user), { replace: true })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login">
      <h1>Sign in</h1>
      <form onSubmit={submit} noValidate>
        {error && <p className="error" role="alert">{error}</p>}
        <label>
          Username
          <input
            name="username"
            autoComplete="username"
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" disabled={submitting}>{submitting ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </main>
  )
}
