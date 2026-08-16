export type EvaluationCriterion = {
  id: number
  projectId: number
  name: string
  description?: string
  maxScore: number
  displayOrder: number
  isActive: boolean
  createdByUserId?: number
  createdByName?: string
  createdAt?: string
}

export type ScoreItem = {
  criterionId: number
  criterionName: string
  maxScore: number
  score: number
  note?: string
}

export type Evaluation = {
  id: number
  projectId: number
  projectName: string
  evaluatorUserId: number
  evaluatorName: string
  evaluatedUserId: number
  evaluatedUserName: string
  note?: string
  scores: ScoreItem[]
  createdAt: string
  updatedAt: string
}

export type CreateCriterionPayload = {
  name: string
  description?: string
  maxScore: number
  displayOrder: number
}

export type UpdateCriterionPayload = {
  name?: string
  description?: string
  maxScore?: number
  displayOrder?: number
  isActive?: boolean
}

export type CreateEvaluationScoreEntry = {
  criterionId: number
  score: number
  note?: string
}

export type CreateEvaluationPayload = {
  evaluatedUserId: string
  note?: string
  scores: CreateEvaluationScoreEntry[]
}

export type UpdateEvaluationPayload = {
  note?: string
  scores?: CreateEvaluationScoreEntry[]
}
