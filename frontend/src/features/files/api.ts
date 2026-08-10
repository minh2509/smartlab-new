import { apiClient } from '../../lib/apiClient'
import type { FileResponse } from '../../shared/types/api'

export type FileAccessScope = 'PRIVATE' | 'LAB' | 'PUBLIC'

export function uploadFile(
  token: string,
  file: File,
  accessScope: FileAccessScope,
  description: string,
) {
  const body = new FormData()
  body.append('file', file)
  body.append('accessScope', accessScope)
  if (description.trim()) body.append('description', description.trim())

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
