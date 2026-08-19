import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  ContentCategory,
  CreatePostRequest,
  CursorPage,
  CommentScope,
  CommentSort,
  PostComment,
  PostDetail,
  PostFeedItem,
  PostSummary,
  ReactionState,
  ReactionType,
  PostReactionUser,
  ReviewPostRequest,
  UpdatePostRequest,
} from './types'

export function listPosts(token?: string | null, cursor?: string, limit = 15) {
  return apiClient<CursorPage<PostFeedItem>>(`/posts${toQuery({ cursor, limit })}`, { token: token ?? null })
}

export function listMyPosts(token: string) {
  return apiClient<PostSummary[]>('/posts/mine', { token })
}

export function setPostReaction(token: string, postId: number, reaction: ReactionType) {
  return apiClient<ReactionState>(`/posts/${postId}/reaction`, {
    method: 'PUT', token, body: JSON.stringify({ reaction }),
  })
}

export function removePostReaction(token: string, postId: number) {
  return apiClient<ReactionState>(`/posts/${postId}/reaction`, { method: 'DELETE', token })
}

export type ListPostCommentsOptions = {
  cursor?: string | null
  limit?: number
  sort?: CommentSort
  scope?: CommentScope
}

export function listPostComments(token: string, postId: number, options: ListPostCommentsOptions = {}) {
  const { cursor, limit = 20, sort = 'NEWEST', scope = 'ALL' } = options
  return apiClient<CursorPage<PostComment>>(
    `/posts/${postId}/comments${toQuery({ cursor: cursor ?? undefined, limit, sort, scope })}`,
    { token },
  )
}

export function listPostReactions(token: string, postId: number, options: { reaction?: ReactionType | null; cursor?: string | null; limit?: number } = {}) {
  const { reaction, cursor, limit = 30 } = options
  return apiClient<CursorPage<PostReactionUser>>(
    `/posts/${postId}/reactions${toQuery({ reaction: reaction ?? undefined, cursor: cursor ?? undefined, limit })}`,
    { token },
  )
}

export function createPostComment(token: string, postId: number, content: string) {
  return apiClient<PostComment>(`/posts/${postId}/comments`, {
    method: 'POST', token, body: JSON.stringify({ content }),
  })
}

export function updatePostComment(token: string, postId: number, commentId: number, content: string) {
  return apiClient<PostComment>(`/posts/${postId}/comments/${commentId}`, {
    method: 'PATCH', token, body: JSON.stringify({ content }),
  })
}

export function deletePostComment(token: string, postId: number, commentId: number) {
  return apiClient<void>(`/posts/${postId}/comments/${commentId}`, { method: 'DELETE', token })
}

export function getPostBySlug(token: string | null | undefined, slug: string) {
  const path = token
    ? `/posts/${encodeURIComponent(slug)}`
    : `/posts/public/${encodeURIComponent(slug)}`
  return apiClient<PostDetail>(path, { token: token ?? null })
}

export async function downloadPostContentFile(token: string | null | undefined, slug: string, fileId: number) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  const response = await fetch(`${baseUrl}/posts/${encodeURIComponent(slug)}/files/${fileId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: 'include',
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(payload?.message ?? `Không thể tải tệp đính kèm (HTTP ${response.status}).`)
  }
  return {
    blob: await response.blob(),
    filename: fileNameFromDisposition(response.headers.get('content-disposition')),
  }
}

function fileNameFromDisposition(value: string | null) {
  const encoded = value?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) return decodeURIComponent(encoded)
  return value?.match(/filename="?([^";]+)"?/i)?.[1] ?? null
}

export function listContentCategories(token: string) {
  return apiClient<ContentCategory[]>('/content-categories', { token })
}

export function createPost(token: string, request: CreatePostRequest) {
  return apiClient<PostDetail>('/posts', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  })
}

export function updatePost(token: string, id: string | number, request: UpdatePostRequest) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(String(id))}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(request),
  })
}

export function submitPost(token: string, id: string | number) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(String(id))}/submit`, {
    method: 'POST',
    token,
  })
}

export function publishPost(token: string, id: string | number) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(String(id))}/publish`, {
    method: 'POST',
    token,
  })
}

export function directPublishPost(token: string, id: string | number) {
  return apiClient<PostDetail>(`/posts/${encodeURIComponent(String(id))}/direct-publish`, {
    method: 'POST',
    token,
  })
}

export function deletePost(token: string, id: string | number) {
  return apiClient<void>(`/posts/${encodeURIComponent(String(id))}`, {
    method: 'DELETE',
    token,
  })
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
