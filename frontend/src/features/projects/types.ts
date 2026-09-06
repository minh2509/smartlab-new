export const PROJECT_TYPES = ['RESEARCH', 'PRODUCTION'] as const

export type ProjectType = (typeof PROJECT_TYPES)[number]

export const PROJECT_STATUSES = [
  'PROPOSED',
  'PREPARING',
  'IN_PROGRESS',
  'PAUSED',
  'COMPLETED',
  'CLOSED',
] as const

export type ProjectStatus = (typeof PROJECT_STATUSES)[number]

export const PUBLIC_PROJECT_STATUSES = ['RECRUITING', 'UPCOMING', 'ACTIVE', 'COMPLETED'] as const

export type PublicProjectStatus = (typeof PUBLIC_PROJECT_STATUSES)[number]

export type ProjectLeader = {
  userId: string
  name: string
}

export type PublicProjectLeader = {
  name: string
}

export type PublicProjectSummary = {
  id: number
  code: string
  name: string
  description: string | null
  goal: string | null
  projectType: ProjectType
  publicStatus: PublicProjectStatus
  researchFields: ProjectResearchField[]
  leaders: PublicProjectLeader[]
}

export type ProjectLeaderCandidate = {
  userId: string
  name: string
  email: string
}

export type Project = {
  id: number
  code: string
  name: string
  description: string | null
  goal: string | null
  projectType: ProjectType
  status: ProjectStatus
  publicStatus?: PublicProjectStatus | null
  startDate: string | null
  expectedEndDate: string | null
  actualEndDate: string | null
  isPublic: boolean
  isFeatured: boolean
  isRecruiting: boolean
  researchFields?: ProjectResearchField[]
  primaryLeader: ProjectLeader | null
  leaders: ProjectLeader[]
  createdAt: string | null
  updatedAt: string | null
}

export type PublicProjectDetail = {
  id: number
  code: string
  name: string
  description: string | null
  goal: string | null
  projectType: ProjectType
  publicStatus: PublicProjectStatus
  startDate: string | null
  expectedEndDate: string | null
  actualEndDate: string | null
  isFeatured: boolean
  researchFields: ProjectResearchField[]
  primaryLeader: PublicProjectLeader | null
  leaders: PublicProjectLeader[]
}

export type CreateProjectPayload = {
  code: string
  name: string
  description?: string
  goal?: string
  projectType?: ProjectType
  status?: ProjectStatus
  startDate?: string
  expectedEndDate?: string
  actualEndDate?: string
  isPublic?: boolean
  isFeatured?: boolean
  leaderUserId?: string
  additionalLeaderUserIds?: string[]
}

export type UpdateProjectPayload = {
  code?: string
  name?: string
  description?: string
  goal?: string
  projectType?: ProjectType
  status?: ProjectStatus
  startDate?: string
  expectedEndDate?: string
  actualEndDate?: string
  isPublic?: boolean
  isFeatured?: boolean
}

export type UpdateProjectLeadershipPayload = {
  primaryLeaderUserId: string | null
  leaderUserIds: string[]
}

export const PROJECT_MEMBER_ROLES = ['LEADER', 'MEMBER'] as const

export type ProjectMemberRole = (typeof PROJECT_MEMBER_ROLES)[number]

export const PROJECT_MEMBER_STATUSES = ['ACTIVE', 'REMOVED'] as const

export type ProjectMemberStatus = (typeof PROJECT_MEMBER_STATUSES)[number]

export type ProjectMember = {
  userId: string
  name: string
  email: string
  projectRole: ProjectMemberRole
  status: ProjectMemberStatus
  joinedAt: string
  removedAt: string | null
}

export type ProjectMemberCandidate = {
  userId: string
  name: string
  email: string
}

export type ProjectMembershipHistory = {
  projectId: number
  projectCode: string
  projectName: string
  projectStatus: ProjectStatus
  projectRole: ProjectMemberRole
  status: ProjectMemberStatus
  joinedAt: string
  removedAt: string | null
}

export const PROJECT_JOIN_REQUEST_STATUSES = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
] as const

export type ProjectJoinRequestStatus = (typeof PROJECT_JOIN_REQUEST_STATUSES)[number]

export type ProjectJoinRequestDecision = 'APPROVE' | 'REJECT'

export type ProjectJoinRequest = {
  id: number
  projectId: number
  projectCode: string
  projectName: string
  requesterUserId: string
  requesterName: string
  requesterEmail: string
  message: string | null
  status: ProjectJoinRequestStatus
  reviewedByUserId: string | null
  reviewedByName: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export type ProjectResearchField = {
  id: number
  code: string
  name: string
  isActive: boolean
}

export type ProjectMemberFilter = ProjectMemberStatus | 'ALL'

export const PROJECT_TYPE_LABELS: Record<ProjectType, string> = {
  RESEARCH: 'Nghiên cứu',
  PRODUCTION: 'Sản phẩm',
}

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  PROPOSED: 'Đề xuất',
  PREPARING: 'Chuẩn bị',
  IN_PROGRESS: 'Đang thực hiện',
  PAUSED: 'Tạm dừng',
  COMPLETED: 'Hoàn thành',
  CLOSED: 'Đã đóng',
}

export const PUBLIC_PROJECT_STATUS_LABELS: Record<PublicProjectStatus, string> = {
  RECRUITING: 'Đang tuyển',
  UPCOMING: 'Sắp triển khai',
  ACTIVE: 'Đang thực hiện',
  COMPLETED: 'Đã hoàn thành',
}

export const PROJECT_STATUS_BADGES: Record<ProjectStatus, string> = {
  PROPOSED: 'info',
  PREPARING: 'warn',
  IN_PROGRESS: 'ok',
  PAUSED: 'warn',
  COMPLETED: 'ok',
  CLOSED: 'mute',
}

export const PROJECT_MEMBER_ROLE_LABELS: Record<ProjectMemberRole, string> = {
  LEADER: 'Leader',
  MEMBER: 'Thành viên',
}

export const PROJECT_MEMBER_STATUS_LABELS: Record<ProjectMemberStatus, string> = {
  ACTIVE: 'Đang tham gia',
  REMOVED: 'Đã rời dự án',
}

export const PROJECT_JOIN_REQUEST_STATUS_LABELS: Record<ProjectJoinRequestStatus, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã chấp nhận',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã hủy',
}
