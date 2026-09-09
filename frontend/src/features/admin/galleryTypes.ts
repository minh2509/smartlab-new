import type { GalleryCategory } from '../public/galleryTypes'

export type GalleryStatus = 'DRAFT' | 'PUBLISHED'
export type AdminGalleryItem = {
  id: number
  title: string
  caption: string | null
  altText: string
  category: Exclude<GalleryCategory, 'ALL'>
  projectId: number | null
  projectName: string | null
  eventId: number | null
  eventTitle: string | null
  fileId: number | null
  originalFileName: string | null
  mimeType: string | null
  sizeBytes: number | null
  capturedAt: string | null
  status: GalleryStatus
  isFeatured: boolean
  publishedAt: string | null
  updatedAt: string | null
}

export type AdminGalleryPage = { items: AdminGalleryItem[]; page: number; size: number; totalElements: number; totalPages: number }

export type GalleryMetadataPayload = { title: string; caption?: string | null; altText: string; category?: Exclude<GalleryCategory, 'ALL'>; projectId?: number | null; eventId?: number | null; capturedAt?: string | null; isFeatured?: boolean }
