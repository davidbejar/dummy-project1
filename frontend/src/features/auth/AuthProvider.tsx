import { ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { onUnauthorized } from '../../lib/http.ts'
import { authApi, Visitor } from './api.ts'
import { AuthContext, AuthState } from './authContext.ts'

/** Used when the backend can't say what anonymous visitors may open: the login page only. */
const DEFAULT_VISITOR: Visitor = { role: 'ANONYMOUS', roleDisplayName: 'Not logged', screens: ['LOGIN'] }

async function anonymousState(): Promise<AuthState> {
  const visitor = await authApi.anonymous().catch(() => DEFAULT_VISITOR)
  return { status: 'anonymous', user: null, visitor }
}

/** Knows who is signed in. Asks the backend once on start, then tracks login and logout. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })

  useEffect(() => {
    let active = true
    const update = (next: AuthState) => active && setState(next)
    authApi
      .me()
      .then((user) => update({ status: 'authenticated', user }))
      .catch(() => anonymousState().then(update))
    // Any 401 from the API means the session is gone (expired, or logged out elsewhere).
    const unsubscribe = onUnauthorized(() => anonymousState().then(update))
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
      setState(await anonymousState())
    }
  }, [])

  const refresh = useCallback(async () => {
    const user = await authApi.me()
    setState({ status: 'authenticated', user })
  }, [])

  const value = useMemo(() => ({ state, login, logout, refresh }), [state, login, logout, refresh])
  return <AuthContext value={value}>{children}</AuthContext>
}
