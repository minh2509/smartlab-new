import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  ContentCategory,
  CreatePostRequest,
  CursorPage,
  PostComment,
  PostDetail,
  PostFeedItem,
  PostSummary,
  ReactionState,
  ReactionType,
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

export function listPostComments(token: string, postId: number, cursor?: string, limit = 20) {
  return apiClient<CursorPage<PostComment>>(
    `/posts/${postId}/comments${toQuery({ cursor, limit })}`,
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
