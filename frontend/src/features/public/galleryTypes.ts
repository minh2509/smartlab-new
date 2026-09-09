export const GALLERY_CATEGORIES = ['ALL', 'WORKSHOP', 'PROJECT_DEMO', 'EVENT', 'LAB_ACTIVITY', 'OTHER'] as const
export type GalleryCategory = (typeof GALLERY_CATEGORIES)[number]

export const GALLERY_SORTS = ['LATEST', 'OLDEST'] as const
export type GallerySort = (typeof GALLERY_SORTS)[number]

export type PublicGalleryItem = {
  id: number
  title: string
  caption: string | null
  altText: string
  category: Exclude<GalleryCategory, 'ALL'>
  projectId: number | null
  projectCode: string | null
  projectName: string | null
  eventId: number | null
  eventTitle: string | null
  fileId: number
  originalFileName: string
  mimeType: string
  sizeBytes: number
  capturedAt: string | null
  publishedAt: string
  isFeatured: boolean
}

export type PublicGalleryPage = {
  items: PublicGalleryItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
