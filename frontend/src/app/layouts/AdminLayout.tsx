import { Files, FlaskConical, FolderKanban, LogOut, ShieldCheck, UserCircle, UsersRound } from 'lucide-react'
import { NavLink, Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { Logo } from '../../shared/components/Logo'

export function AdminLayout() {
  const { isAuthenticated, profile, logout } = useAuth()
  const can = (...permissions: string[]) => permissions.every((permission) => profile?.permissions.includes(permission))

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
              {can('FILE_UPLOAD') && <NavLink to="/files">
                <Files />
                Tệp của tôi
              </NavLink>}
              <span className="admin-nav-label">Quản trị</span>
              {(can('PROJECT_READ') || profile?.roles.includes('ADMIN')) && <NavLink to="/admin/projects">
                <FolderKanban />
                Dự án
              </NavLink>}
              {can('USER_MANAGE', 'ROLE_MANAGE', 'PERMISSION_MANAGE') && <NavLink to="/admin/accounts">
                <UsersRound />
                Quản trị tài khoản
              </NavLink>}
              {can('ROLE_MANAGE', 'PERMISSION_MANAGE') && <NavLink to="/admin/rbac">
                <ShieldCheck />
                Vai trò & quyền
              </NavLink>}
              {can('RESEARCH_FIELD_MANAGE') && <NavLink to="/admin/research-fields">
                <FlaskConical />
                Lĩnh vực nghiên cứu
              </NavLink>}
              {can('MEMBER_MANAGE') && <NavLink to="/admin/members">
                <UserCircle />
                Hồ sơ thành viên
              </NavLink>}
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
