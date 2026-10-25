import { FormEvent, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from './useAuth.ts'

const HOME_PATH = '/todos'

export function LoginPage() {
  const { state, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const from = (location.state as { from?: string } | null)?.from ?? HOME_PATH

  if (state.status === 'loading') return <p className="loading" role="status">Loading…</p>
  if (state.status === 'authenticated') return <Navigate to={from} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username and password')
      return
    }
    setSubmitting(true)
    try {
      await login(username.trim(), password)
      navigate(from, { replace: true })
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
