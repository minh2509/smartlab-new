export const EVENT_MODES = ['IN_PERSON', 'ONLINE'] as const

export type EventMode = (typeof EVENT_MODES)[number]

export const EVENT_STATUSES = ['SCHEDULED', 'CANCELLED', 'COMPLETED'] as const

export type EventStatus = (typeof EVENT_STATUSES)[number]

export const EVENT_VISIBILITIES = ['PUBLIC', 'LAB', 'PROJECT'] as const

export type EventVisibility = (typeof EVENT_VISIBILITIES)[number]

export type EventCreator = {
  userId: string
  name: string
}

export type LabEvent = {
  id: number
  projectId: number | null
  creator: EventCreator | null
  title: string
  content: string | null
  mode: EventMode
  location: string | null
  meetingUrl: string | null
  startAt: string
  endAt: string | null
  status: EventStatus
  visibility: EventVisibility
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export type CreateEventPayload = {
  projectId: number | null
  title: string
  content?: string | null
  mode: EventMode
  location?: string | null
  meetingUrl?: string | null
  startAt: string
  endAt?: string | null
  status?: EventStatus
  visibility: EventVisibility
}

export type UpdateEventPayload = Omit<Partial<CreateEventPayload>, 'projectId'>

export type EventListFilters = {
  projectId?: number
  status?: EventStatus
  upcoming?: boolean
}

export const EVENT_MODE_LABELS: Record<EventMode, string> = {
  IN_PERSON: 'Trực tiếp',
  ONLINE: 'Trực tuyến',
}

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  SCHEDULED: 'Đã lên lịch',
  CANCELLED: 'Đã hủy',
  COMPLETED: 'Đã kết thúc',
}

export const EVENT_STATUS_BADGES: Record<EventStatus, string> = {
  SCHEDULED: 'info',
  CANCELLED: 'danger',
  COMPLETED: 'mute',
}

export const EVENT_VISIBILITY_LABELS: Record<EventVisibility, string> = {
  PUBLIC: 'Công khai',
  LAB: 'Toàn Lab',
  PROJECT: 'Trong dự án',
}
