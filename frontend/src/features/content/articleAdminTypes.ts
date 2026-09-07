export type LabArticleStatus = 'DRAFT' | 'PUBLISHED'

export type ArticleContentFileReference = {
  type: 'image' | 'file'
  fileId: number
  alt?: string | null
  label?: string | null
}

export type ArticleContentDocument = {
  body: string
  files?: ArticleContentFileReference[]
}

export type AdminLabArticle = {
  id: number
  title: string
  slug: string
  excerpt: string | null
  content: Record<string, unknown>
  status: LabArticleStatus
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type AdminLabArticlePage = {
  items: AdminLabArticle[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type CreateAdminArticlePayload = {
  title: string
  slug?: string
  excerpt?: string | null
  content: ArticleContentDocument
  status?: LabArticleStatus
  publishedAt?: string
}

export type UpdateAdminArticlePayload = {
  title: string
  slug?: string
  excerpt: string | null
  content: ArticleContentDocument
  status: LabArticleStatus
  publishedAt?: string
}
