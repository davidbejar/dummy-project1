import { ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { onUnauthorized } from '../../lib/http.ts'
import { authApi } from './api.ts'
import { AuthContext, AuthState } from './authContext.ts'

const ANONYMOUS: AuthState = { status: 'anonymous', user: null }

/** Knows who is signed in. Asks the backend once on start, then tracks login and logout. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })

  useEffect(() => {
    let active = true
    authApi
      .me()
      .then((user) => active && setState({ status: 'authenticated', user }))
      .catch(() => active && setState(ANONYMOUS))
    // Any 401 from the API means the session is gone (expired, or logged out elsewhere).
    const unsubscribe = onUnauthorized(() => setState(ANONYMOUS))
    return () => {
      active = false
      unsubscribe()
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const user = await authApi.login(username, password)
    setState({ status: 'authenticated', user })
    return user
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setState(ANONYMOUS)
    }
  }, [])

  const value = useMemo(() => ({ state, login, logout }), [state, login, logout])
  return <AuthContext value={value}>{children}</AuthContext>
}
