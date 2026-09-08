export type AuthResponse = {
  email: string
  token: string
  sessionId: string
}

export type AccountResponse = {
  userId: string
  name: string
  email: string
  isActive: boolean
  isAccountVerified: boolean
  roles: string[]
  permissions: string[]
}

export type PaginatedResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type InvitationResponse = {
  email: string
  status: string
  expiresAt: string
  sentTo: string
}

export type BulkInvitationRow = {
  fullName: string
  email: string
}

export type BulkInvitationItemResponse = {
  sourceRow: number
  itemId?: string
  fullName: string
  email: string
  status: string
  failureCode?: string
  failureMessage?: string
  resendCount: number
}

export type BulkInvitationPreviewResponse = {
  requestedCount: number
  acceptedCount: number
  rejectedCount: number
  items: BulkInvitationItemResponse[]
}

export type BulkInvitationBatchResponse = BulkInvitationPreviewResponse & {
  batchId: string
  status: string
  createdBy: string
  createdAt: string
  completedAt?: string
  roleCodes: string[]
}

export type Role = {
  id: number
  code: string
  name: string
  description?: string
  isSystem: boolean
  isActive: boolean
  permissionCodes?: string[]
}

export type Permission = {
  id: number
  code: string
  name: string
  module?: string
  description?: string
  isActive: boolean
}

export type FileResponse = {
  id: number
  originalName: string
  mimeType: string
  sizeBytes: number
  accessScope: string
  description?: string
  createdAt?: string
}

export type ResearchField = {
  id: number
  code: string
  name: string
  description?: string
  coverFileId?: number | null
  isActive: boolean
}

export type MemberProfile = {
  userId: string
  name: string
  email?: string
  publicEmail?: string
  phone?: string
  bio?: string
  joinedLabAt?: string
  activeStatus: string
  isFeatured: boolean
  featuredOrder?: number
  avatar?: FileResponse
  researchFields: ResearchField[]
  roles?: string[]
  permissions?: string[]
}

export type ApiErrorPayload = {
  error?: boolean
  message?: string
}
