import { apiClient, toQuery } from '../../lib/apiClient'
import type { AchievementPage, YearCount } from './achievementTypes'

export function listAchievementYears(): Promise<YearCount[]> {
  return apiClient<YearCount[]>('/achievements/years')
}

export function listAchievements(year?: number, page = 0, size = 3): Promise<AchievementPage> {
  return apiClient<AchievementPage>(`/achievements${toQuery({ year, page, size })}`)
}
