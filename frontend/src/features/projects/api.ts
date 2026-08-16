import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  CreateProjectPayload,
  Project,
  ProjectLeaderCandidate,
  ProjectMember,
  ProjectMemberCandidate,
  ProjectMemberStatus,
  ProjectResearchField,
  UpdateProjectLeadershipPayload,
  UpdateProjectPayload,
} from './types'

export function listProjects(token?: string | null, filters: { researchFieldId?: number } = {}) {
  return apiClient<Project[]>(`/projects${toQuery(filters)}`, { token: token ?? null })
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

export function listProjectMembers(token: string, projectId: number, status: ProjectMemberStatus = 'ACTIVE') {
  return apiClient<ProjectMember[]>(`/projects/${projectId}/members${toQuery({ status })}`, { token })
}

export function listProjectMemberCandidates(
  token: string,
  projectId: number,
  query: string,
  signal?: AbortSignal,
) {
  return apiClient<ProjectMemberCandidate[]>(
    `/projects/${projectId}/member-candidates${toQuery({ query: query.trim() })}`,
    { token, signal },
  )
}

export function addProjectMember(token: string, projectId: number, userId: string) {
  return apiClient<ProjectMember>(`/projects/${projectId}/members`, {
    method: 'POST',
    token,
    body: JSON.stringify({ userId }),
  })
}

export function removeProjectMember(token: string, projectId: number, userId: string) {
  return apiClient<void>(`/projects/${projectId}/members/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
    token,
  })
}

export function listProjectResearchFields(token: string, projectId: number) {
  return apiClient<ProjectResearchField[]>(`/projects/${projectId}/research-fields`, { token })
}

export function replaceProjectResearchFields(token: string, projectId: number, fieldIds: number[]) {
  return apiClient<ProjectResearchField[]>(`/projects/${projectId}/research-fields`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ fieldIds }),
  })
}
