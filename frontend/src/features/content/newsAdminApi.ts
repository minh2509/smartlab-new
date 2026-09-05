import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  AdminLabNewsArticle,
  AdminLabNewsArticlePage,
  CreateAdminNewsPayload,
  UpdateAdminNewsPayload,
} from './newsAdminTypes'

export function listAdminNews(token: string, page = 0, size = 20, signal?: AbortSignal) {
  return apiClient<AdminLabNewsArticlePage>(`/admin/news${toQuery({ page, size })}`, { token, signal })
}

export function createAdminNews(token: string, payload: CreateAdminNewsPayload) {
  return apiClient<AdminLabNewsArticle>('/admin/news', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateAdminNews(token: string, id: number, payload: UpdateAdminNewsPayload) {
  return apiClient<AdminLabNewsArticle>(`/admin/news/${id}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function deleteAdminNews(token: string, id: number) {
  return apiClient<void>(`/admin/news/${id}`, {
    method: 'DELETE',
    token,
  })
}
