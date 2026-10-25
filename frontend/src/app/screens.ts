/**
 * The screens this frontend implements, each with its route. Screens themselves are data in the
 * backend's app_screen table, and which roles may open them comes from the backend; this list
 * only ties each code to a page (screens.test.ts checks the codes exist in the migration).
 */
export const Screen = {
  LOGIN: 'LOGIN',
  TODO: 'TODO',
  ADMINISTRATION: 'ADMINISTRATION',
  USER_ADMIN: 'USER_ADMIN',
  ROLE_ADMIN: 'ROLE_ADMIN',
} as const

export type ScreenCode = (typeof Screen)[keyof typeof Screen]

export const SCREEN_PATHS: Record<ScreenCode, string> = {
  LOGIN: '/',
  TODO: '/todos',
  ADMINISTRATION: '/admin',
  USER_ADMIN: '/admin/users',
  ROLE_ADMIN: '/admin/roles',
}

// Signed-in users land on the first of these they may open.
const HOME_SCREENS: ScreenCode[] = [Screen.TODO, Screen.ADMINISTRATION, Screen.USER_ADMIN, Screen.ROLE_ADMIN]

/** Whether `path` is a screen these screen codes allow opening. */
export function canOpenPath(path: string, screens: string[]): boolean {
  return Object.entries(SCREEN_PATHS).some(([code, screenPath]) => screenPath === path && screens.includes(code))
}

/** Where a signed-in user starts. Without any screen it is Todos, which then says why not. */
export function homePath(screens: string[]): string {
  const home = HOME_SCREENS.find((screen) => screens.includes(screen)) ?? Screen.TODO
  return SCREEN_PATHS[home]
}
