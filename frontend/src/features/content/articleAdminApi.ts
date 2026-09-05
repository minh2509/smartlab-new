import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  AdminLabArticle,
  AdminLabArticlePage,
  CreateAdminArticlePayload,
  UpdateAdminArticlePayload,
} from './articleAdminTypes'

export function listAdminArticles(token: string, page = 0, size = 20, signal?: AbortSignal) {
  return apiClient<AdminLabArticlePage>(`/admin/articles${toQuery({ page, size })}`, { token, signal })
}

export function createAdminArticle(token: string, payload: CreateAdminArticlePayload) {
  return apiClient<AdminLabArticle>('/admin/articles', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateAdminArticle(token: string, id: number, payload: UpdateAdminArticlePayload) {
  return apiClient<AdminLabArticle>(`/admin/articles/${id}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function deleteAdminArticle(token: string, id: number) {
  return apiClient<void>(`/admin/articles/${id}`, {
    method: 'DELETE',
    token,
  })
}
