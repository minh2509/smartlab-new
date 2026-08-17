import { apiClient, toQuery } from '../../lib/apiClient'
import type { CreateEventPayload, EventListFilters, LabEvent, UpdateEventPayload } from './types'

export function listEvents(token: string, filters: EventListFilters = {}, signal?: AbortSignal) {
  return apiClient<LabEvent[]>(`/events${toQuery(filters)}`, { token, signal })
}

export function listPublicEvents(filters: Omit<EventListFilters, 'projectId'> = {}, signal?: AbortSignal) {
  return apiClient<LabEvent[]>(`/events/public${toQuery(filters)}`, { signal })
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
