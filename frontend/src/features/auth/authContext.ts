import { createContext } from 'react'
import { CurrentUser } from './api.ts'

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null }
  | { status: 'authenticated'; user: CurrentUser }

export interface AuthContextValue {
  state: AuthState
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
}

// Kept apart from the provider component so hot reloading can replace the component alone.
export const AuthContext = createContext<AuthContextValue | null>(null)
