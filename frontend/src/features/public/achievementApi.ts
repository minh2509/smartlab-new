import { apiClient, toQuery } from '../../lib/apiClient'
import type { AchievementPage, AchievementDetail, YearCount } from './achievementTypes'

export function listAchievementYears(): Promise<YearCount[]> {
  return apiClient<YearCount[]>('/achievements/years')
}

export function listAchievements(year?: number, page = 0, size = 3): Promise<AchievementPage> {
  return apiClient<AchievementPage>(`/achievements${toQuery({ year, page, size })}`)
}

export function getAchievement(id: number): Promise<AchievementDetail> {
  return apiClient<AchievementDetail>(`/achievements/${id}`)
}
