import { request } from '../../lib/http.ts'

export type Role = 'USER' | 'ADMIN'

/** Mirrors the backend CurrentUser record. */
export interface CurrentUser {
  username: string
  displayName: string
  role: Role
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
