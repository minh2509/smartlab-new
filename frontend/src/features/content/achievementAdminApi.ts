import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  AdminLabAchievement,
  AdminLabAchievementPage,
  AchievementType,
  CreateAdminAchievementPayload,
  UpdateAdminAchievementPayload,
  AdminAchievementAttachment,
} from './achievementAdminTypes'

export function listAdminAchievements(
  token: string,
  params: { page?: number; size?: number; year?: number; type?: AchievementType; isPublic?: boolean; q?: string },
  signal?: AbortSignal,
) {
  return apiClient<AdminLabAchievementPage>(`/admin/achievements${toQuery(params)}`, { token, signal })
}

export function createAdminAchievement(token: string, payload: CreateAdminAchievementPayload) {
  return apiClient<AdminLabAchievement>('/admin/achievements', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateAdminAchievement(token: string, id: number, payload: UpdateAdminAchievementPayload) {
  return apiClient<AdminLabAchievement>(`/admin/achievements/${id}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function deleteAdminAchievement(token: string, id: number) {
  return apiClient<void>(`/admin/achievements/${id}`, {
    method: 'DELETE',
    token,
  })
}

export function listAchievementFiles(token: string, id: number) {
  return apiClient<AdminAchievementAttachment[]>(`/admin/achievements/${id}/files`, { token })
}

export function uploadAchievementFile(token: string, id: number, file: File, label?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (label && label.trim() !== '') {
    formData.append('label', label.trim())
  }
  return apiClient<AdminAchievementAttachment>(`/admin/achievements/${id}/files`, {
    method: 'POST',
    token,
    body: formData,
  })
}

export function deleteAchievementFile(token: string, id: number, attachmentId: number) {
  return apiClient<void>(`/admin/achievements/${id}/files/${attachmentId}`, {
    method: 'DELETE',
    token,
  })
}
