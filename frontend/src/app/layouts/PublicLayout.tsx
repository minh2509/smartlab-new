import { Outlet } from 'react-router-dom'
import { AppHeader } from '../../shared/components/AppHeader'

export function PublicLayout() {
  return (
    <div className="app-page">
      <AppHeader />
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
