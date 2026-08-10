import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  CreateProjectPayload,
  Project,
  ProjectLeaderCandidate,
  UpdateProjectLeadershipPayload,
  UpdateProjectPayload,
} from './types'

export function listProjects(token?: string | null) {
  return apiClient<Project[]>('/projects', { token: token ?? null })
}

export function getProject(id: number, token?: string | null) {
  return apiClient<Project>(`/projects/${id}`, { token: token ?? null })
}

export function createProject(token: string, payload: CreateProjectPayload) {
  return apiClient<Project>('/projects', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateProject(token: string, id: number, payload: UpdateProjectPayload) {
  return apiClient<Project>(`/projects/${id}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function changeProjectLeader(token: string, id: number, leaderUserId: string) {
  return apiClient<Project>(`/projects/${id}/leader`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ leaderUserId }),
  })
}

export function replaceProjectLeaders(token: string, id: number, leaderUserIds: string[]) {
  return apiClient<Project>(`/projects/${id}/leaders`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ leaderUserIds }),
  })
}

export function listProjectLeaderCandidates(token: string, query: string, signal?: AbortSignal) {
  return apiClient<ProjectLeaderCandidate[]>(`/projects/leader-candidates${toQuery({ query: query.trim() })}`, {
    token,
    signal,
  })
}

export function updateProjectLeadership(
  token: string,
  id: number,
  payload: UpdateProjectLeadershipPayload,
) {
  return apiClient<Project>(`/projects/${id}/leadership`, {
    method: 'PUT',
    token,
    body: JSON.stringify(payload),
  })
}

export function deleteProject(token: string, id: number) {
  return apiClient<void>(`/projects/${id}`, {
    method: 'DELETE',
    token,
  })
}
