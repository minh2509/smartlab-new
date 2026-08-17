export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type AttachmentType = 'INPUT' | 'RESULT' | 'REFERENCE'

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'Cần làm',
  IN_PROGRESS: 'Đang làm',
  REVIEW: 'Chờ duyệt',
  DONE: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
}

export const TASK_STATUS_BADGES: Record<TaskStatus, string> = {
  TODO: 'badge-secondary',
  IN_PROGRESS: 'badge-info',
  REVIEW: 'badge-warning',
  DONE: 'badge-success',
  CANCELLED: 'badge-danger',
}

export const TASK_PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Thấp',
  MEDIUM: 'Trung bình',
  HIGH: 'Cao',
  URGENT: 'Khẩn cấp',
}

export const TASK_PRIORITY_BADGES: Record<TaskPriority, string> = {
  LOW: 'badge-secondary',
  MEDIUM: 'badge-info',
  HIGH: 'badge-warning',
  URGENT: 'badge-danger',
}

export type TaskAssignee = {
  userId: number
  accountUserId: string
  name: string
  email: string
  assignedAt?: string
}

export type TaskAttachment = {
  id: number
  fileId: number
  originalName: string
  mimeType: string
  sizeBytes: number
  attachmentType: AttachmentType
  description?: string
  uploadedByUserId?: number
  uploadedByName?: string
  createdAt?: string
}

export type TaskSummary = {
  id: number
  projectId: number
  parentTaskId?: number
  title: string
  status: TaskStatus
  priority: TaskPriority
  startAt?: string
  dueAt?: string
  createdAt: string
  updatedAt: string
  assignees: TaskAssignee[]
  attachmentCount: number
}

export type TaskDetail = {
  id: number
  projectId: number
  parentTaskId?: number
  title: string
  description?: string
  status: TaskStatus
  priority: TaskPriority
  startAt?: string
  dueAt?: string
  createdByUserId?: number
  createdByName?: string
  createdAt: string
  updatedAt: string
  assignees: TaskAssignee[]
  attachments: TaskAttachment[]
}

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type CreateTaskPayload = {
  title: string
  description?: string
  priority: TaskPriority
  status?: TaskStatus
  parentTaskId?: number
  startAt?: string
  dueAt?: string
}

export type UpdateTaskPayload = {
  title?: string
  description?: string
  status?: TaskStatus
  priority?: TaskPriority
  parentTaskId?: number
  startAt?: string
  dueAt?: string
}

export type AddAssigneesPayload = {
  userIds: number[]
}

export type AddAttachmentPayload = {
  fileId: number
  attachmentType: AttachmentType
  description?: string
}

export type SubmitTaskPayload = {
  fileId: number
  note?: string
}
