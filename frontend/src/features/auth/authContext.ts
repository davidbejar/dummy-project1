import { createContext } from 'react'
import { CurrentUser, Visitor } from './api.ts'

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null; visitor: Visitor }
  | { status: 'authenticated'; user: CurrentUser }

export interface AuthContextValue {
  state: AuthState
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  /** Reloads the signed-in user, e.g. after their role's screens changed. */
  refresh: () => Promise<void>
}

/** Codes of the screens the current visitor or user may open. */
export function screensOf(state: AuthState): string[] {
  if (state.status === 'authenticated') return state.user.screens
  if (state.status === 'anonymous') return state.visitor.screens
  return []
}

// Kept apart from the provider component so hot reloading can replace the component alone.
export const AuthContext = createContext<AuthContextValue | null>(null)
