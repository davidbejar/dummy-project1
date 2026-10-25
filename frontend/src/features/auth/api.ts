import { request } from '../../lib/http.ts'

/** Mirrors the backend Visitor record: what someone who is not signed in may open. */
export interface Visitor {
  /** Role code, e.g. 'ANONYMOUS', 'USER' or 'ADMIN'. Roles live in the database, so this is open-ended. */
  role: string
  roleDisplayName: string
  /** Codes of the screens this role may open (see app/screens.ts). */
  screens: string[]
}

/** Mirrors the backend CurrentUser record. */
export interface CurrentUser extends Visitor {
  username: string
  displayName: string
}

export const authApi = {
  /** The signed-in user; rejects with a 401 ApiError when there is no session. */
  me: () => request<CurrentUser>('/auth/me'),
  /** The role and screens of visitors without a session. */
  anonymous: () => request<Visitor>('/auth/anonymous'),
  login: (username: string, password: string) =>
    request<CurrentUser>('/auth/login', {
      method: 'POST',
      // Spring Security's form login reads form fields, not JSON.
      body: new URLSearchParams({ username, password }),
    }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
}
