import { request } from '../../lib/http.ts'

/** Mirrors the backend CurrentUser record. */
export interface CurrentUser {
  username: string
  displayName: string
  /** Role code, e.g. 'USER' or 'ADMIN'. Roles live in the database, so this is open-ended. */
  role: string
  roleDisplayName: string
}

export const authApi = {
  /** The signed-in user; rejects with a 401 ApiError when there is no session. */
  me: () => request<CurrentUser>('/auth/me'),
  login: (username: string, password: string) =>
    request<CurrentUser>('/auth/login', {
      method: 'POST',
      // Spring Security's form login reads form fields, not JSON.
      body: new URLSearchParams({ username, password }),
    }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
}
