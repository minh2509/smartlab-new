export interface LabNewsArticle {
  id: number
  title: string
  excerpt: string | null
  sourceName: string
  sourceUrl: string
  publishedAt: string
}

export type PublicNewsSort = 'LATEST' | 'OLDEST'
