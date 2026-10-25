import { request } from '../../lib/http.ts'

/** Mirrors the backend UserAdminController.UserView record. */
export interface AdminUser {
  id: string // UUID
  username: string
  displayName: string
  role: string
  roleDisplayName: string
  /** False for users that must always exist (admin and david). */
  deletable: boolean
}

/** A role a user can be given. */
export interface RoleOption {
  code: string
  displayName: string
}

export interface NewUser {
  username: string
  displayName: string
  password: string
  role: string
}

/** Mirrors the backend RoleAdminController.RoleView record. */
export interface AdminRole {
  code: string
  displayName: string
  /** Codes of the screens this role may open. */
  screens: string[]
  /** Screens whose assignment cannot be changed (all of ADMIN's). */
  lockedScreens: string[]
  deletable: boolean
}

/** A screen roles can be given. The login screen is not one: it belongs to ANONYMOUS alone. */
export interface ScreenInfo {
  code: string
  displayName: string
  /** The screen this one is reached through. Giving a role this screen gives it the parent too. */
  parentCode: string | null
}

export const usersApi = {
  list: () => request<AdminUser[]>('/admin/users'),
  roles: () => request<RoleOption[]>('/admin/users/roles'),
  create: (user: NewUser) => request<AdminUser>('/admin/users', { method: 'POST', body: JSON.stringify(user) }),
  remove: (id: string) => request<void>(`/admin/users/${id}`, { method: 'DELETE' }),
}

export const rolesApi = {
  list: () => request<AdminRole[]>('/admin/roles'),
  screens: () => request<ScreenInfo[]>('/admin/roles/screens'),
  create: (code: string, displayName: string) =>
    request<AdminRole>('/admin/roles', { method: 'POST', body: JSON.stringify({ code, displayName }) }),
  remove: (code: string) => request<void>(`/admin/roles/${encodeURIComponent(code)}`, { method: 'DELETE' }),
  assign: (code: string, screen: string) =>
    request<AdminRole>(`/admin/roles/${encodeURIComponent(code)}/screens/${screen}`, { method: 'PUT' }),
  unassign: (code: string, screen: string) =>
    request<AdminRole>(`/admin/roles/${encodeURIComponent(code)}/screens/${screen}`, { method: 'DELETE' }),
}
