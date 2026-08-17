import { apiClient, toQuery } from '../../lib/apiClient'
import type {
  AddAssigneesPayload,
  AddAttachmentPayload,
  CreateTaskPayload,
  PageResponse,
  SubmitTaskPayload,
  TaskAssignee,
  TaskAttachment,
  TaskDetail,
  TaskPriority,
  TaskStatus,
  TaskSummary,
  UpdateTaskPayload,
} from './types'

export function listTasks(
  token: string,
  projectId: number,
  params: {
    status?: TaskStatus
    priority?: TaskPriority
    assigneeUserId?: number
    page?: number
    size?: number
  } = {},
) {
  return apiClient<PageResponse<TaskSummary>>(
    `/projects/${projectId}/tasks${toQuery(params)}`,
    { token },
  )
}

export function getTaskDetail(token: string, taskId: number) {
  return apiClient<TaskDetail>(`/tasks/${taskId}`, { token })
}

export function createTask(token: string, projectId: number, payload: CreateTaskPayload) {
  return apiClient<TaskDetail>(`/projects/${projectId}/tasks`, {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateTask(token: string, taskId: number, payload: UpdateTaskPayload) {
  return apiClient<TaskDetail>(`/tasks/${taskId}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function deleteTask(token: string, taskId: number) {
  return apiClient<void>(`/tasks/${taskId}`, {
    method: 'DELETE',
    token,
  })
}

export function addTaskAssignees(token: string, taskId: number, payload: AddAssigneesPayload) {
  return apiClient<TaskAssignee[]>(`/tasks/${taskId}/assignees`, {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function removeTaskAssignee(token: string, taskId: number, userId: number) {
  return apiClient<void>(`/tasks/${taskId}/assignees/${userId}`, {
    method: 'DELETE',
    token,
  })
}

export function addTaskAttachment(token: string, taskId: number, payload: AddAttachmentPayload) {
  return apiClient<TaskAttachment>(`/tasks/${taskId}/attachments`, {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function uploadTaskAttachment(
  token: string,
  taskId: number,
  file: File,
  attachmentType: AddAttachmentPayload['attachmentType'],
  description?: string,
) {
  const body = new FormData()
  body.append('file', file)
  body.append('attachmentType', attachmentType)
  if (description) body.append('description', description)
  return apiClient<TaskAttachment>(`/tasks/${taskId}/attachments/upload`, {
    method: 'POST',
    token,
    body,
  })
}

export function submitTask(token: string, taskId: number, payload: SubmitTaskPayload) {
  return apiClient<TaskDetail>(`/tasks/${taskId}/submit`, {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function uploadTaskSubmission(token: string, taskId: number, file: File, note?: string) {
  const body = new FormData()
  body.append('file', file)
  if (note) body.append('note', note)
  return apiClient<TaskDetail>(`/tasks/${taskId}/submit/upload`, {
    method: 'POST',
    token,
    body,
  })
}
