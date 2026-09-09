import { apiClient, toQuery } from '../../lib/apiClient'
import type { PublicGalleryPage, PublicGalleryItem, GalleryCategory, GallerySort } from './galleryTypes'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'

export function listPublicGallery(params: { query?: string; category?: GalleryCategory; projectId?: number; year?: number; sort?: GallerySort; page?: number; size?: number }, signal?: AbortSignal) {
  return apiClient<PublicGalleryPage>(`/gallery/public${toQuery({ q: params.query?.trim() || undefined, category: params.category && params.category !== 'ALL' ? params.category : undefined, projectId: params.projectId, year: params.year, sort: params.sort && params.sort !== 'LATEST' ? params.sort : undefined, page: params.page ?? 0, size: params.size ?? 24 })}`, { signal })
}

export function listPublicGalleryYears(signal?: AbortSignal) {
  return apiClient<number[]>('/gallery/public/years', { signal })
}

export function publicGalleryFileUrl(fileId: number) { return `${API_BASE_URL}/files/${encodeURIComponent(fileId)}` }

export type { PublicGalleryItem }
