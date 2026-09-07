export interface LabArticleSummary {
  id: number
  title: string
  slug: string
  excerpt: string | null
  publishedAt: string
}

export interface LabArticleDetail {
  id: number
  title: string
  slug: string
  excerpt: string | null
  content: Record<string, unknown>
  publishedAt: string
}
