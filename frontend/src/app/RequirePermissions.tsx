import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/authContext'
import { hasAllPermissions } from './accessPolicy'

export function RequirePermissions({ allOf, children }: { allOf: readonly string[]; children: ReactNode }) {
  const { isAuthenticated, profile } = useAuth()

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!profile) return <div className="empty">Đang tải quyền truy cập...</div>
  if (!hasAllPermissions(profile.permissions, allOf)) {
    return <Navigate to="/profile" replace />
  }
  return children
}
