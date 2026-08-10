import { apiClient } from '../../lib/apiClient'
import type { PostDetail, PostSummary } from './types'

export function listPosts(token: string) {
  return apiClient<PostSummary[]>('/posts', { token })
}

export function getPostBySlug(token: string, slug: string) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(slug)}`, { token })
}
