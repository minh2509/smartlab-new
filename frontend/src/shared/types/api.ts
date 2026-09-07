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
