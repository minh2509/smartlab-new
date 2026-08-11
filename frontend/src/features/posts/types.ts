export type PostStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'REVISION_REQUIRED'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'REJECTED'

export type PostVisibility = 'PUBLIC' | 'LAB' | 'PROJECT'

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

export type PostSummary = {
  id: number
  title: string
  slug: string
  excerpt: string | null
  visibility: PostVisibility
  status: PostStatus
  category: PostCategory | null
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type PostDetail = PostSummary & {
  contentJson: Record<string, unknown>
}
