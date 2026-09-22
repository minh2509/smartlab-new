import type { ReactNode } from 'react'
import { ShieldX } from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/authContext'
import { hasAllPermissions } from './accessPolicy'

export function RequirePermissions({ allOf, roles, children }: { allOf: readonly string[]; roles?: readonly string[]; children: ReactNode }) {
  const { isAuthenticated, isHydrating, profile } = useAuth()

  if (isHydrating) return <div className="empty">Đang tải quyền truy cập...</div>
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!profile) return <div className="empty">Đang tải quyền truy cập...</div>
  if (!hasAllPermissions(profile.permissions, allOf) || (roles && !roles.some((role) => profile.roles.includes(role)))) {
    return (
      <section className="route-access-denied" role="alert" aria-labelledby="access-denied-title">
        <span className="route-access-denied-icon"><ShieldX aria-hidden="true" /></span>
        <div>
          <span className="eyebrow">403 · Quyền truy cập</span>
          <h1 id="access-denied-title">Bạn không có quyền mở trang này</h1>
          <p>Tài khoản của bạn chưa được cấp quyền sử dụng chức năng này. Vui lòng liên hệ quản trị viên nếu bạn cần quyền truy cập.</p>
          <Link className="btn primary" to="/">Về trang chủ</Link>
        </div>
      </section>
    )
  }
  return children
}
