import { ReactNode } from 'react'
import { Navigate, RouteObject } from 'react-router'
import { AppLayout } from '../components/AppLayout.tsx'
import { AdministrationPage } from '../features/admin/AdministrationPage.tsx'
import { RolesAdminPage } from '../features/admin/RolesAdminPage.tsx'
import { UsersAdminPage } from '../features/admin/UsersAdminPage.tsx'
import { LoginPage } from '../features/auth/LoginPage.tsx'
import { RequireScreen } from '../features/auth/RequireScreen.tsx'
import { TodosPage } from '../features/todos/TodosPage.tsx'
import { Screen, SCREEN_PATHS, ScreenCode } from './screens.ts'

/** A screen's route, rendered only for roles that may open it. */
function screen(code: ScreenCode, element: ReactNode): RouteObject {
  return { element: <RequireScreen screen={code} />, children: [{ path: SCREEN_PATHS[code], element }] }
}

/**
 * All pages, each under AppLayout so they share the header. New screens add a row to
 * app_screen in the backend, a code in screens.ts and a screen(...) route here.
 */
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      // Login stays reachable: the backend never lets ANONYMOUS lose it.
      { path: SCREEN_PATHS.LOGIN, element: <LoginPage /> },
      screen(Screen.TODO, <TodosPage />),
      screen(Screen.ADMINISTRATION, <AdministrationPage />),
      screen(Screen.USER_ADMIN, <UsersAdminPage />),
      screen(Screen.ROLE_ADMIN, <RolesAdminPage />),
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
