import { apiClient, toQuery } from '../../lib/apiClient'
import type { PageResponse } from './publicPageTypes'
import type { PublicDocumentFileType, PublicDocumentSort, PublicDocumentSummary } from './documentTypes'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'

export type PublicDocumentFilters = {
  query?: string
  projectId?: number
  fileType?: PublicDocumentFileType
  year?: number
  sort?: PublicDocumentSort
}

export function listPublicDocuments(page = 0, size = 12, filters: PublicDocumentFilters = {}, signal?: AbortSignal) {
  return apiClient<PageResponse<PublicDocumentSummary>>(`/documents/public${toQuery({
    q: filters.query?.trim() || undefined,
    projectId: filters.projectId,
    fileType: filters.fileType && filters.fileType !== 'ALL' ? filters.fileType : undefined,
    year: filters.year,
    sort: filters.sort && filters.sort !== 'LATEST' ? filters.sort : undefined,
    page,
    size,
  })}`, { signal })
}

export function listPublicDocumentYears(signal?: AbortSignal) {
  return apiClient<number[]>('/documents/public/years', { signal })
}

export function publicDocumentFileUrl(fileId: number) {
  return `${API_BASE_URL}/files/${encodeURIComponent(String(fileId))}`
}
