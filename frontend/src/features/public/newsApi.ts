import { apiClient, toQuery } from '../../lib/apiClient'
import type { LabNewsArticle, PublicNewsSort } from './newsTypes'
import type { PageResponse } from './publicPageTypes'


export function listLatestNews(limit = 3): Promise<LabNewsArticle[]> {
  return apiClient<LabNewsArticle[]>(`/news${toQuery({ limit })}`)
}

export type NewsArchiveOptions = {
  q?: string
  source?: string
  year?: number
  sort?: PublicNewsSort
  page?: number
  size?: number
  signal?: AbortSignal
}

export function listNewsArchive({ q, source, year, sort = 'LATEST', page = 0, size = 12, signal }: NewsArchiveOptions = {}): Promise<PageResponse<LabNewsArticle>> {
  return apiClient(`/news/archive${toQuery({ q: q?.trim() || undefined, source: source?.trim() || undefined, year, sort: sort === 'LATEST' ? undefined : sort, page, size })}`, { signal })
}

export function listNewsSources(signal?: AbortSignal): Promise<string[]> {
  return apiClient('/news/archive/sources', { signal })
}

export function listNewsYears(signal?: AbortSignal): Promise<number[]> {
  return apiClient('/news/archive/years', { signal })
}
