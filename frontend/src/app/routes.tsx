import { Navigate, RouteObject } from 'react-router'
import { AppLayout } from '../components/AppLayout.tsx'
import { LoginPage } from '../features/auth/LoginPage.tsx'
import { RequireAuth } from '../features/auth/RequireAuth.tsx'
import { TodosPage } from '../features/todos/TodosPage.tsx'

/**
 * All pages. Public pages sit at the top level; pages that need a signed-in user go under
 * RequireAuth + AppLayout, so new features only add a child route there.
 */
export const routes: RouteObject[] = [
  { path: '/', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [{ path: '/todos', element: <TodosPage /> }],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
