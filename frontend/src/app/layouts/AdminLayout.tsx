import { KeyRound, LogOut, ShieldCheck, UserCircle, UsersRound } from 'lucide-react'
import { NavLink, Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { Logo } from '../../shared/components/Logo'

export function AdminLayout() {
  const { isAuthenticated, logout } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return (
    <div className="admin-shell">
      <div className="route-shell">
        <aside className="admin-sidebar">
          <div className="sticky">
            <div className="admin-brand">
              <Logo />
              <span>Admin Workspace</span>
            </div>

            <nav className="admin-nav" aria-label="Admin navigation">
              <span className="admin-nav-label">Tổng quan</span>
              <NavLink to="/profile">
                <UserCircle />
                Tài khoản của tôi
              </NavLink>
              <span className="admin-nav-label">Quản trị</span>
              <NavLink to="/admin/accounts">
                <UsersRound />
                Quản trị tài khoản
              </NavLink>
              <NavLink to="/admin/rbac">
                <ShieldCheck />
                Vai trò & quyền
              </NavLink>
              <NavLink to="/forgot-password">
                <KeyRound />
                Đổi mật khẩu
              </NavLink>
            </nav>

            <button className="admin-logout-btn admin-logout-bottom" type="button" onClick={() => void logout()}>
              <LogOut size={15} />
              Đăng xuất
            </button>
          </div>
        </aside>

        <section className="admin-workspace">
          <header className="admin-topbar">
            <div>
              <span>Smart Lab</span>
              <strong>Trang quản trị nội bộ</strong>
            </div>
          </header>
          <main className="admin-content">
            <Outlet />
          </main>
        </section>
      </div>
    </div>
  )
}
