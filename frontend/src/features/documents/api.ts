import { apiClient } from '../../lib/apiClient'
import type {
  CreateDocumentVersionPayload,
  CreateProjectDocumentPayload,
  DocumentVersion,
  ProjectDocument,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'

export function listProjectDocuments(token: string, projectId: number) {
  return apiClient<ProjectDocument[]>(`/projects/${projectId}/documents`, { token })
}

export function createProjectDocument(
  token: string,
  projectId: number,
  payload: CreateProjectDocumentPayload,
) {
  const body = new FormData()
  body.append('file', payload.file)
  body.append('title', payload.title.trim())
  body.append('description', payload.description.trim())
  body.append('accessScope', payload.accessScope)
  body.append('note', payload.note.trim())

  return apiClient<ProjectDocument>(`/projects/${projectId}/documents`, {
    method: 'POST',
    token,
    body,
  })
}

export function listDocumentVersions(token: string, documentId: number) {
  return apiClient<DocumentVersion[]>(`/documents/${documentId}/versions`, { token })
}

export function createDocumentVersion(
  token: string,
  documentId: number,
  payload: CreateDocumentVersionPayload,
) {
  const body = new FormData()
  body.append('file', payload.file)
  body.append('accessScope', payload.accessScope)
  body.append('note', payload.note.trim())

  return apiClient<DocumentVersion>(`/documents/${documentId}/versions`, {
    method: 'POST',
    token,
    body,
  })
}

export function deleteDocument(token: string, documentId: number) {
  return apiClient<void>(`/documents/${documentId}`, {
    method: 'DELETE',
    token,
  })
}

export async function downloadDocument(token: string, documentId: number) {
  const response = await fetch(`${API_BASE_URL}/documents/${documentId}/download`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: 'include',
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(payload?.message ?? `Không thể tải tài liệu (HTTP ${response.status}).`)
  }

  return {
    blob: await response.blob(),
    filename: filenameFromContentDisposition(response.headers.get('Content-Disposition')),
  }
}

function filenameFromContentDisposition(value: string | null) {
  if (!value) return null

  const encodedMatch = /filename\*=UTF-8''([^;]+)/i.exec(value)
  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1].trim())
    } catch {
      return encodedMatch[1].trim()
    }
  }

  const quotedMatch = /filename="([^"]+)"/i.exec(value)
  if (quotedMatch?.[1]) return quotedMatch[1]

  const plainMatch = /filename=([^;]+)/i.exec(value)
  return plainMatch?.[1]?.trim() ?? null
}
