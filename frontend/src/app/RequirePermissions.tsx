import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/authContext'

export function RequirePermissions({ allOf, children }: { allOf: string[]; children: ReactNode }) {
  const { isAuthenticated, profile } = useAuth()

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!profile) return <div className="empty">Đang tải quyền truy cập...</div>
  if (!allOf.every((permission) => profile.permissions.includes(permission))) {
    return <Navigate to="/profile" replace />
  }
  return children
}
