import { apiClient } from '../../lib/apiClient'
import type { PostDetail, PostSummary, ReviewPostRequest } from './types'

export function listPosts(token: string) {
  return apiClient<PostSummary[]>('/posts', { token })
}

export function getPostBySlug(token: string, slug: string) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(slug)}`, { token })
}

export function listReviewablePosts(token: string) {
  return apiClient<PostSummary[]>('/posts/review-queue', { token })
}

export function getReviewablePost(token: string, id: string | number) {
  return apiClient<PostDetail>(`/posts/review-queue/${encodeURIComponent(String(id))}`, { token })
}

export function reviewPost(token: string, id: string | number, request: ReviewPostRequest) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(String(id))}/reviews`, {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  })
}
