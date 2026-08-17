import { apiClient } from '../../lib/apiClient'
import type { FileResponse } from '../../shared/types/api'

export type FileAccessScope = 'PRIVATE' | 'LAB' | 'PROJECT' | 'PUBLIC'

export function listOwnFiles(token: string) {
  return apiClient<FileResponse[]>('/me/files', { token })
}

export const D2_UPLOAD_ACCEPT = [
  'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'application/pdf', 'text/plain', 'text/csv',
  'application/zip', 'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint', 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
].join(',')

export function uploadFile(
  token: string,
  file: File,
  accessScope: FileAccessScope,
  description: string,
  projectId?: number,
) {
  const body = new FormData()
  body.append('file', file)
  body.append('accessScope', accessScope)
  if (description.trim()) body.append('description', description.trim())
  if (projectId !== undefined) body.append('projectId', String(projectId))

  return apiClient<FileResponse>('/files/upload', {
    method: 'POST',
    token,
    body,
  })
}

export async function downloadFile(token: string, fileId: number) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  const response = await fetch(`${baseUrl}/files/${fileId}`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: 'include',
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(payload?.message ?? `Không thể tải file (HTTP ${response.status}).`)
  }
  return response.blob()
}

export function deleteFile(token: string, fileId: number) {
  return apiClient<void>(`/files/${fileId}`, {
    method: 'DELETE',
    token,
  })
}
