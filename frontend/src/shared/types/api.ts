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
}

export type ApiErrorPayload = {
  error?: boolean
  message?: string
}
