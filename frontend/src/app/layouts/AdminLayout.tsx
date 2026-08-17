import { Award, CalendarDays, CheckSquare, Files, FlaskConical, FolderKanban, LogOut, ShieldCheck, UserCircle, UsersRound } from 'lucide-react'
import { NavLink, Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { Logo } from '../../shared/components/Logo'
import { accessPolicies, hasAllPermissions } from '../accessPolicy'

export function AdminLayout() {
  const { isAuthenticated, profile, logout } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  if (!profile) {
    return <div className="empty">Đang tải không gian làm việc...</div>
  }

  const workspace = resolveWorkspacePresentation(profile.roles)
  const can = (policy: readonly string[]) => hasAllPermissions(profile.permissions, policy)

  return (
    <div className="admin-shell">
      <div className="route-shell">
        <aside className="admin-sidebar">
          <div className="sticky">
            <div className="admin-brand">
              <Logo />
              <span>{workspace.brand}</span>
            </div>

            <nav className="admin-nav" aria-label="Điều hướng không gian làm việc">
              <span className="admin-nav-label">Tổng quan</span>
              <NavLink to="/profile">
                <UserCircle />
                Tài khoản của tôi
              </NavLink>
              {can(accessPolicies.files) && <NavLink to="/files">
                <Files />
                Tệp của tôi
              </NavLink>}
              <span className="admin-nav-label">{workspace.managementLabel}</span>
              {can(accessPolicies.projects) && <NavLink to="/admin/projects">
                <FolderKanban />
                Dự án
              </NavLink>}
              <NavLink to="/admin/events">
                <CalendarDays />
                Sự kiện
              </NavLink>
              {can(accessPolicies.tasks) && <NavLink to="/admin/tasks">
                <CheckSquare />
                Nhiệm vụ & Đánh giá
              </NavLink>
              }
              <NavLink to="/my-evaluations">
                <Award />
                Đánh giá của tôi
              </NavLink>
              {can(accessPolicies.accounts) && <NavLink to="/admin/accounts">
                <UsersRound />
                Quản trị tài khoản
              </NavLink>}
              {can(accessPolicies.rbac) && <NavLink to="/admin/rbac">
                <ShieldCheck />
                Vai trò & quyền
              </NavLink>}
              {can(accessPolicies.researchFields) && <NavLink to="/admin/research-fields">
                <FlaskConical />
                Lĩnh vực nghiên cứu
              </NavLink>}
              {can(accessPolicies.members) && <NavLink to="/admin/members">
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
              <strong>{workspace.heading}</strong>
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

function resolveWorkspacePresentation(roles: readonly string[]) {
  if (roles.includes('ADMIN')) {
    return {
      brand: 'Admin Workspace',
      heading: 'Trang quản trị nội bộ',
      managementLabel: 'Quản trị',
    }
  }
  if (roles.includes('LEADER')) {
    return {
      brand: 'Leader Workspace',
      heading: 'Không gian làm việc Leader',
      managementLabel: 'Công việc',
    }
  }
  if (roles.includes('MEMBER')) {
    return {
      brand: 'Member Workspace',
      heading: 'Không gian thành viên',
      managementLabel: 'Công việc',
    }
  }
  return {
    brand: 'Smart Lab Workspace',
    heading: 'Không gian nội bộ',
    managementLabel: 'Công việc',
  }
}
