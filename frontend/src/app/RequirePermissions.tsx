import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/authContext'
import { hasAllPermissions } from './accessPolicy'

export function RequirePermissions({ allOf, roles, children }: { allOf: readonly string[]; roles?: readonly string[]; children: ReactNode }) {
  const { isAuthenticated, isHydrating, profile } = useAuth()

  if (isHydrating) return <div className="empty">Đang tải quyền truy cập...</div>
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!profile) return <div className="empty">Đang tải quyền truy cập...</div>
  if (!hasAllPermissions(profile.permissions, allOf) || (roles && !roles.some((role) => profile.roles.includes(role)))) {
    return <Navigate to="/profile" replace />
  }
  return children
}
