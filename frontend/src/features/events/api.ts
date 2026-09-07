import { apiClient, toQuery } from '../../lib/apiClient'
import type { CreateEventPayload, EventListFilters, EventStatus, LabEvent, UpdateEventPayload } from './types'

export function listEvents(token: string, filters: EventListFilters = {}, signal?: AbortSignal) {
  return apiClient<LabEvent[]>(`/events${toQuery(filters)}`, { token, signal })
}

export function listPublicEvents(filters: Omit<EventListFilters, 'projectId'> = {}, signal?: AbortSignal) {
  return apiClient<LabEvent[]>(`/events/public${toQuery(filters)}`, { signal })
}

export type PublicEventPage = {
  items: LabEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type PublicEventFilters = {
  status?: EventStatus
  upcoming?: boolean
  query?: string
}

export function listPublicEventArchive(
  page = 0,
  size = 12,
  filters: PublicEventFilters = {},
  signal?: AbortSignal,
) {
  return apiClient<PublicEventPage>(`/events/public/archive${toQuery({
    page,
    size,
    status: filters.status,
    upcoming: filters.upcoming,
    q: filters.query?.trim() || undefined,
  })}`, { signal })
}

export function getPublicEvent(eventId: number, signal?: AbortSignal) {
  return apiClient<LabEvent>(`/events/public/${eventId}`, { signal })
}

export function getEvent(token: string, eventId: number) {
  return apiClient<LabEvent>(`/events/${eventId}`, { token })
}

export function createEvent(token: string, payload: CreateEventPayload) {
  return apiClient<LabEvent>('/events', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateEvent(token: string, eventId: number, payload: UpdateEventPayload) {
  return apiClient<LabEvent>(`/events/${eventId}`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(payload),
  })
}

export function deleteEvent(token: string, eventId: number) {
  return apiClient<void>(`/events/${eventId}`, {
    method: 'DELETE',
    token,
  })
}
