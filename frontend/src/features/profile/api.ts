import type { FileResponse, MemberProfile, ResearchField } from '../../shared/types/api'
import { apiClient, toQuery } from '../../lib/apiClient'

export function getMyMemberProfile(token: string) {
  return apiClient<MemberProfile>('/me/profile', { token })
}

export function getResearchFields() {
  return apiClient<ResearchField[]>('/research-fields')
}

export function updateMyMemberProfile(
  token: string,
  payload: {
    phone: string
    publicEmail: string
    bio: string
    avatarFileId?: number
    removeAvatar?: boolean
    researchFieldIds: number[]
  },
) {
  return apiClient<MemberProfile>('/me/profile', {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function uploadAvatar(token: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  body.append('accessScope', 'PUBLIC')
  body.append('description', 'Smart Lab member avatar')
  return apiClient<FileResponse>('/files/upload', {
    method: 'POST',
    token,
    body,
  })
}

export function downloadFile(token: string, fileId: number) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  return fetch(`${baseUrl}/files/${fileId}`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: 'include',
  }).then(async (response) => {
    if (!response.ok) throw new Error(`File request failed with status ${response.status}`)
    return response.blob()
  })
}

export function getMembers(params: { keyword?: string; fieldCode?: string } = {}) {
  return apiClient<MemberProfile[]>(`/members${toQuery(params)}`)
}
