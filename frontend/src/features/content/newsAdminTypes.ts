export type AdminLabNewsArticle = {
  id: number
  title: string
  excerpt: string | null
  sourceName: string
  sourceUrl: string
  publishedAt: string
  isPublic: boolean
  createdAt: string
  updatedAt: string
}

export type AdminLabNewsArticlePage = {
  items: AdminLabNewsArticle[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type CreateAdminNewsPayload = {
  title: string
  excerpt?: string | null
  sourceName: string
  sourceUrl: string
  publishedAt: string
  isPublic: boolean
}

export type UpdateAdminNewsPayload = CreateAdminNewsPayload
