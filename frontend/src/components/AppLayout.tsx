import { Outlet } from 'react-router'
import { AppHeader } from './AppHeader.tsx'

/** Shell for every screen: the shared header, then the page. */
export function AppLayout() {
  return (
    <>
      <AppHeader />
      <Outlet />
    </>
  )
}
