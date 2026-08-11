import { apiClient } from '../../lib/apiClient'
import type { Notification } from './types'

export function listNotifications(token: string) {
  return apiClient<Notification[]>('/me/notifications', { token })
}

export function markNotificationRead(token: string, id: string | number) {
  return apiClient<void>(`/me/notifications/${encodeURIComponent(String(id))}/read`, {
    method: 'PATCH',
    token,
  })
}

export function markAllNotificationsRead(token: string) {
  return apiClient<void>('/me/notifications/read-all', {
    method: 'PATCH',
    token,
  })
}

export function deleteNotification(token: string, id: string | number) {
  return apiClient<void>(`/me/notifications/${encodeURIComponent(String(id))}`, {
    method: 'DELETE',
    token,
  })
}
