import { apiClient, toQuery } from '../../lib/apiClient'
import type { LabArticleDetail, LabArticleSummary, PublicArticleSort } from './articleTypes'
import type { PageResponse } from './publicPageTypes'

export async function listLatestArticles(limit = 3): Promise<LabArticleSummary[]> {
  return apiClient(`/articles/latest${toQuery({ limit })}`)
}

interface ListArticlesOptions {
  q?: string
  year?: number
  sort?: PublicArticleSort
  page?: number
  size?: number
  signal?: AbortSignal
}

export async function listArticles({ q, year, sort = 'LATEST', page = 0, size = 12, signal }: ListArticlesOptions = {}): Promise<PageResponse<LabArticleSummary>> {
  return apiClient(`/articles${toQuery({ q: q || undefined, year, sort: sort === 'LATEST' ? undefined : sort, page, size })}`, { signal })
}

export async function listArticleYears(signal?: AbortSignal): Promise<number[]> {
  return apiClient('/articles/years', { signal })
}

export async function getArticle(slug: string): Promise<LabArticleDetail> {
  return apiClient(`/articles/${encodeURIComponent(slug)}`)
}
