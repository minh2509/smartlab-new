export type PostStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'REVISION_REQUIRED'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'REJECTED'

export type PostVisibility = 'PUBLIC' | 'LAB' | 'PROJECT'

export type ReactionType = 'LIKE' | 'LOVE' | 'HAHA' | 'SAD' | 'ANGRY'
export type CommentSort = 'NEWEST' | 'OLDEST' | 'UPDATED_NEWEST' | 'UPDATED_OLDEST'
export type CommentScope = 'ALL' | 'MINE'

export type ReviewDecision = 'APPROVED' | 'REVISION_REQUIRED' | 'REJECTED'

export type ReviewPostRequest = {
  decision: ReviewDecision
  reason?: string | null
}

export type PostCategory = {
  id: number
  code: string
  name: string
}

export type PostAuthor = {
  userId: string
  name: string
}

export type PostReviewFeedback = {
  decision: ReviewDecision
  reason: string
  createdAt: string
}

export type PostContentImageReference = {
  type: 'image'
  fileId: number
  alt?: string
}

export type PostContentFileReference = {
  type: 'file'
  fileId: number
  label?: string
}

export type PostContentAttachmentReference = PostContentImageReference | PostContentFileReference

export type PostContentDocument = {
  type: 'doc'
  body: string
  files?: PostContentAttachmentReference[]
}

export type ContentCategory = PostCategory & {
  description: string | null
}

export type CreatePostRequest = {
  title: string
  excerpt?: string | null
  contentJson?: Record<string, unknown>
  visibility?: PostVisibility
  categoryId?: number | null
  projectId?: number | null
}

export type UpdatePostRequest = {
  title?: string
  excerpt?: string | null
  contentJson?: Record<string, unknown> | null
  visibility?: PostVisibility
  categoryId?: number | null
  projectId?: number | null
}

export type PostSummary = {
  id: number
  title: string
  slug: string
  excerpt: string | null
  visibility: PostVisibility
  projectId: number | null
  status: PostStatus
  category: PostCategory | null
  author: PostAuthor | null
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type PostDetail = PostSummary & {
  contentJson: Record<string, unknown>
  reviewFeedback: PostReviewFeedback | null
}

export type CursorPage<T> = {
  items: T[]
  nextCursor: string | null
}

export type ReactionCounts = Record<ReactionType, number>

export type PostFeedItem = Omit<PostSummary, 'status'> & {
  contentJson: Record<string, unknown>
  viewerReaction: ReactionType | null
  reactionCounts: ReactionCounts
  reactionCount: number
  commentCount: number
}

export type ReactionState = {
  postId: number
  viewerReaction: ReactionType | null
  reactionCounts: ReactionCounts
  reactionCount: number
}

export type PostComment = {
  id: number
  content: string
  author: PostAuthor | null
  createdAt: string
  updatedAt: string
}

export type PostReactionUser = {
  user: PostAuthor | null
  reaction: ReactionType
  reactedAt: string
}
