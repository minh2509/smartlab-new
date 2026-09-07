import { apiClient, toQuery } from '../../lib/apiClient'
import type { LabNewsArticle } from './newsTypes'
import type { PageResponse } from './publicPageTypes'


export function listLatestNews(limit = 3): Promise<LabNewsArticle[]> {
  return apiClient<LabNewsArticle[]>(`/news${toQuery({ limit })}`)
}

export function listNewsArchive(page = 0, size = 12, signal?: AbortSignal): Promise<PageResponse<LabNewsArticle>> {
  return apiClient(`/news/archive${toQuery({ page, size })}`, { signal })
}
