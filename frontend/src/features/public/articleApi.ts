import { apiClient, toQuery } from '../../lib/apiClient'
import type { LabArticleDetail, LabArticleSummary } from './articleTypes'
import type { PageResponse } from './publicPageTypes'

export async function listLatestArticles(limit = 3): Promise<LabArticleSummary[]> {
  return apiClient(`/articles/latest${toQuery({ limit })}`)
}

export async function listArticles(page = 0, size = 12, signal?: AbortSignal): Promise<PageResponse<LabArticleSummary>> {
  return apiClient(`/articles${toQuery({ page, size })}`, { signal })
}

export async function getArticle(slug: string): Promise<LabArticleDetail> {
  return apiClient(`/articles/${encodeURIComponent(slug)}`)
}
