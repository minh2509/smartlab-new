import { apiClient, toQuery } from '../../lib/apiClient'
import type { GalleryCategory, GallerySort } from '../public/galleryTypes'
import type { AdminGalleryItem, AdminGalleryPage, GalleryMetadataPayload, GalleryStatus } from './galleryTypes'

export function listAdminGallery(token: string, params: { page?: number; size?: number; q?: string; status?: GalleryStatus; category?: GalleryCategory; projectId?: number; year?: number; sort?: GallerySort }, signal?: AbortSignal) {
  return apiClient<AdminGalleryPage>(`/admin/gallery${toQuery({ page: params.page ?? 0, size: params.size ?? 24, q: params.q?.trim() || undefined, status: params.status, category: params.category && params.category !== 'ALL' ? params.category : undefined, projectId: params.projectId, year: params.year, sort: params.sort })}`, { token, signal })
}

export function createAdminGallery(token: string, file: File, payload: GalleryMetadataPayload) {
  const form = new FormData()
  form.append('file', file)
  form.append('title', payload.title)
  form.append('altText', payload.altText)
  if (payload.caption) form.append('caption', payload.caption)
  if (payload.category) form.append('category', payload.category)
  if (payload.projectId) form.append('projectId', String(payload.projectId))
  if (payload.eventId) form.append('eventId', String(payload.eventId))
  if (payload.capturedAt) form.append('capturedAt', toIsoInstant(payload.capturedAt))
  form.append('isFeatured', String(Boolean(payload.isFeatured)))
  return apiClient<AdminGalleryItem>('/admin/gallery', { method: 'POST', token, body: form })
}

export function updateAdminGallery(token: string, id: number, payload: GalleryMetadataPayload) { return apiClient<AdminGalleryItem>(`/admin/gallery/${id}`, { method: 'PATCH', token, body: JSON.stringify({ ...payload, capturedAt: payload.capturedAt ? toIsoInstant(payload.capturedAt) : null }) }) }
export function publishAdminGallery(token: string, id: number) { return apiClient<AdminGalleryItem>(`/admin/gallery/${id}/publish`, { method: 'POST', token }) }
export function unpublishAdminGallery(token: string, id: number) { return apiClient<AdminGalleryItem>(`/admin/gallery/${id}/unpublish`, { method: 'POST', token }) }
export function deleteAdminGallery(token: string, id: number) { return apiClient<void>(`/admin/gallery/${id}`, { method: 'DELETE', token }) }

function toIsoInstant(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toISOString()
}
