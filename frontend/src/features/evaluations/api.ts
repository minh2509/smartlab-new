import { apiClient } from '../../lib/apiClient'
import type {
  CreateCriterionPayload,
  CreateEvaluationPayload,
  Evaluation,
  EvaluationCriterion,
  UpdateCriterionPayload,
  UpdateEvaluationPayload,
} from './types'

export function listEvaluationCriteria(token: string, projectId: number) {
  return apiClient<EvaluationCriterion[]>(`/projects/${projectId}/evaluation-criteria`, { token })
}

export function createEvaluationCriterion(
  token: string,
  projectId: number,
  payload: CreateCriterionPayload,
) {
  return apiClient<EvaluationCriterion>(`/projects/${projectId}/evaluation-criteria`, {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateEvaluationCriterion(
  token: string,
  projectId: number,
  criterionId: number,
  payload: UpdateCriterionPayload,
) {
  return apiClient<EvaluationCriterion>(
    `/projects/${projectId}/evaluation-criteria/${criterionId}`,
    {
      method: 'PATCH',
      token,
      body: JSON.stringify(payload),
    },
  )
}

export function createEvaluation(token: string, projectId: number, payload: CreateEvaluationPayload) {
  return apiClient<Evaluation>(`/projects/${projectId}/evaluations`, {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateEvaluation(token: string, evaluationId: number, payload: UpdateEvaluationPayload) {
  return apiClient<Evaluation>(`/evaluations/${evaluationId}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function getMyEvaluations(token: string) {
  return apiClient<Evaluation[]>('/me/evaluations', { token })
}
