import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { routes } from './app/routes.tsx'
import { AuthProvider } from './features/auth/AuthProvider.tsx'
import { applyTheme, initialTheme } from './lib/theme.ts'
import './index.css'

applyTheme(initialTheme())

const router = createBrowserRouter(routes)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
