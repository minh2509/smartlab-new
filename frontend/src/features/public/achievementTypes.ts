export const ACHIEVEMENT_TYPES = [
  'RESEARCH_RESULT',
  'PRODUCT',
  'AWARD',
  'CERTIFICATE',
  'MILESTONE',
  'OTHER',
] as const

export type AchievementType = (typeof ACHIEVEMENT_TYPES)[number]

export const ACHIEVEMENT_TYPE_LABELS: Record<AchievementType, string> = {
  RESEARCH_RESULT: 'Kết quả nghiên cứu',
  PRODUCT: 'Sản phẩm',
  AWARD: 'Giải thưởng',
  CERTIFICATE: 'Chứng nhận',
  MILESTONE: 'Dấu mốc',
  OTHER: 'Thành tựu',
}

export type Achievement = {
  id: number
  title: string
  summary: string | null
  achievementType: AchievementType
  achievementYear: number
  achievementDate: string | null
  evidenceUrl: string | null
  recognizingOrganization?: string | null
  relatedProject: {
    id: number
    code: string
    name: string
  } | null
  isPublic: boolean
  createdAt: string
  updatedAt: string
}

export type YearCount = {
  year: number
  count: number
}

export type AchievementPage = {
  items: Achievement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
