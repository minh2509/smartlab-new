export type AchievementType =
  | 'RESEARCH_RESULT'
  | 'PRODUCT'
  | 'AWARD'
  | 'CERTIFICATE'
  | 'MILESTONE'
  | 'OTHER'

export const ACHIEVEMENT_TYPES: AchievementType[] = [
  'RESEARCH_RESULT',
  'PRODUCT',
  'AWARD',
  'CERTIFICATE',
  'MILESTONE',
  'OTHER',
]

export const ACHIEVEMENT_TYPE_LABELS: Record<AchievementType, string> = {
  RESEARCH_RESULT: 'Kết quả nghiên cứu',
  PRODUCT: 'Sản phẩm',
  AWARD: 'Giải thưởng',
  CERTIFICATE: 'Chứng nhận',
  MILESTONE: 'Cột mốc',
  OTHER: 'Khác',
}

export interface AdminLabAchievement {
  id: number
  title: string
  summary: string | null
  achievementType: AchievementType
  achievementYear: number
  achievementDate: string | null
  evidenceUrl: string | null
  relatedProjectId: number | null
  recognizingOrganization?: string | null
  isPublic: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminAchievementAttachment {
  id: number
  fileId: number
  originalName: string
  mimeType: string
  sizeBytes: number
  label?: string | null
  sortOrder: number
  createdAt: string
}

export interface AdminLabAchievementPage {
  items: AdminLabAchievement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateAdminAchievementPayload {
  title: string
  summary?: string | null
  achievementType: AchievementType
  achievementYear: number
  achievementDate?: string | null
  evidenceUrl?: string | null
  relatedProjectId?: number | null
  recognizingOrganization?: string | null
  isPublic?: boolean
}

export interface UpdateAdminAchievementPayload {
  title?: string
  summary?: string | null
  achievementType?: AchievementType
  achievementYear?: number
  achievementDate?: string | null
  evidenceUrl?: string | null
  relatedProjectId?: number | null
  recognizingOrganization?: string | null
  isPublic?: boolean
}
