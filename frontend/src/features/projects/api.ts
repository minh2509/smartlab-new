import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  CreateProjectPayload,
  Project,
  ProjectLeaderCandidate,
  ProjectJoinRequest,
  ProjectJoinRequestDecision,
  ProjectMember,
  ProjectMemberCandidate,
  ProjectMembershipHistory,
  ProjectMemberStatus,
  ProjectResearchField,
  PublicProjectDetail,
  PublicProjectSummary,
  ProjectType,
  PublicProjectStatus,
  UpdateProjectLeadershipPayload,
  UpdateProjectPayload,
} from './types'

export type RecruitingPage = {
  items: PublicProjectSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type PublicProjectPage = RecruitingPage

export type PublicProjectFilters = {
  query?: string
  researchFieldId?: number
  researchFieldCode?: string
  projectType?: ProjectType
  status?: PublicProjectStatus
}

export function listPublicRecruitingProjects(page = 0, size = 6) {
  return apiClient<RecruitingPage>(`/projects/public/recruiting${toQuery({ page, size })}`)
}

export function listPublicProjects(
  page = 0,
  size = 12,
  filters: PublicProjectFilters = {},
  signal?: AbortSignal,
) {
  return apiClient<PublicProjectPage>(`/projects/public${toQuery({
    page,
    size,
    q: filters.query?.trim() || undefined,
    researchFieldId: filters.researchFieldId,
    field: filters.researchFieldCode,
    projectType: filters.projectType,
    status: filters.status,
  })}`, { signal })
}

export function listProjects(token?: string | null, filters: { researchFieldId?: number } = {}) {
  return apiClient<Project[]>(`/projects${toQuery(filters)}`, { token: token ?? null })
}

export function getProject(id: number, token?: string | null) {
  return apiClient<Project>(`/projects/${id}`, { token: token ?? null })
}

export function getPublicProject(id: number, signal?: AbortSignal) {
  return apiClient<PublicProjectDetail>(`/projects/public/${id}`, { signal })
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

export function listMyProjectMemberships(token: string) {
  return apiClient<ProjectMembershipHistory[]>('/projects/memberships/me', { token })
}

export function createProjectJoinRequest(token: string, projectId: number, message?: string) {
  return apiClient<ProjectJoinRequest>(`/projects/${projectId}/join-requests`, {
    method: 'POST',
    token,
    body: JSON.stringify({ message: message?.trim() || undefined }),
  })
}

export function getMyProjectJoinRequest(token: string, projectId: number) {
  return apiClient<ProjectJoinRequest | null>(`/projects/${projectId}/join-requests/me`, { token })
}

export function cancelMyProjectJoinRequest(token: string, projectId: number) {
  return apiClient<void>(`/projects/${projectId}/join-requests/me`, {
    method: 'DELETE',
    token,
  })
}

export function listProjectJoinRequests(token: string, projectId: number) {
  return apiClient<ProjectJoinRequest[]>(
    `/projects/${projectId}/join-requests${toQuery({ status: 'PENDING' })}`,
    { token },
  )
}

export function reviewProjectJoinRequest(
  token: string,
  projectId: number,
  requestId: number,
  decision: ProjectJoinRequestDecision,
) {
  return apiClient<ProjectJoinRequest>(`/projects/${projectId}/join-requests/${requestId}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ decision }),
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
