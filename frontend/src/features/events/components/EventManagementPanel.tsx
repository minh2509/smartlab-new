import { CalendarDays, Edit3, Plus, RefreshCw, Save, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import type { Project } from '../../projects/types'
import { createEvent, deleteEvent, listEvents, updateEvent } from '../api'
import {
  EVENT_MODES,
  EVENT_MODE_LABELS,
  EVENT_STATUSES,
  EVENT_STATUS_BADGES,
  EVENT_STATUS_LABELS,
  EVENT_VISIBILITIES,
  EVENT_VISIBILITY_LABELS,
} from '../types'
import type {
  CreateEventPayload,
  EventMode,
  EventStatus,
  EventVisibility,
  LabEvent,
  UpdateEventPayload,
} from '../types'

type EventScope = 'PROJECT' | 'LAB'
type EventStatusFilter = EventStatus | 'ALL'

type EventForm = {
  title: string
  content: string
  mode: EventMode
  location: string
  meetingUrl: string
  startAt: string
  endAt: string
  status: EventStatus
  visibility: EventVisibility
}

export function EventManagementPanel({ project }: { project: Project | null }) {
  const { token, profile } = useAuth()
  const projectId = project?.id ?? null
  const scope: EventScope = project ? 'PROJECT' : 'LAB'
  const [events, setEvents] = useState<LabEvent[]>([])
  const [statusFilter, setStatusFilter] = useState<EventStatusFilter>('ALL')
  const [upcomingOnly, setUpcomingOnly] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<EventForm>(() => emptyEventForm(project ? 'PROJECT' : 'LAB'))
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const isAdmin = Boolean(profile?.roles.includes('ADMIN') && profile.permissions.includes('PROJECT_MANAGE'))
  const isProjectLeader = Boolean(
    profile && project?.leaders.some((leader) => leader.userId === profile.userId),
  )
  const canManage = isAdmin || (scope === 'PROJECT' && isProjectLeader)
  const availableVisibilities = useMemo(
    () => scope === 'PROJECT'
      ? isAdmin ? [...EVENT_VISIBILITIES] : (['PROJECT'] as EventVisibility[])
      : EVENT_VISIBILITIES.filter((visibility) => visibility !== 'PROJECT'),
    [isAdmin, scope],
  )

  const loadEventList = useCallback(async (signal?: AbortSignal) => {
    if (!token) return
    const result = await listEvents(token, {
      projectId: scope === 'PROJECT' ? project?.id : undefined,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      upcoming: upcomingOnly || undefined,
    }, signal)
    if (!signal?.aborted) {
      setEvents(scope === 'LAB' ? result.filter((event) => event.projectId === null) : result)
    }
  }, [project?.id, scope, statusFilter, token, upcomingOnly])

  useEffect(() => {
    setEditingId(null)
    setForm(emptyEventForm(projectId ? 'PROJECT' : 'LAB'))
    setMessage('')
    setError('')
  }, [projectId])

  useEffect(() => {
    if (!token || (scope === 'PROJECT' && !project)) {
      setEvents([])
      return
    }
    let active = true
    const controller = new AbortController()
    setLoading(true)
    setError('')
    void loadEventList(controller.signal)
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason, 'Không tải được danh sách sự kiện.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [loadEventList, project, scope, token])

  function beginEdit(event: LabEvent) {
    if (!canManageEvent(event)) return
    setEditingId(event.id)
    setForm({
      title: event.title,
      content: event.content ?? '',
      mode: event.mode,
      location: event.location ?? '',
      meetingUrl: event.meetingUrl ?? '',
      startAt: toDateTimeInput(event.startAt),
      endAt: toDateTimeInput(event.endAt),
      status: event.status,
      visibility: event.visibility,
    })
    clearFeedback()
  }

  function resetForm() {
    setEditingId(null)
    setForm(emptyEventForm(scope))
    clearFeedback()
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !canManage || saving || (scope === 'PROJECT' && !project)) return
    const validationError = validateEventForm(form)
    if (validationError) {
      setError(validationError)
      return
    }

    setSaving(true)
    clearFeedback()
    const commonPayload = toPayload(form)
    try {
      if (editingId === null) {
        const payload: CreateEventPayload = {
          ...commonPayload,
          projectId: scope === 'PROJECT' ? project!.id : null,
        }
        const created = await createEvent(token, payload)
        setMessage(`Đã tạo sự kiện “${created.title}”.`)
      } else {
        const payload: UpdateEventPayload = commonPayload
        const updated = await updateEvent(token, editingId, payload)
        setMessage(`Đã cập nhật sự kiện “${updated.title}”.`)
      }
      setEditingId(null)
      setForm(emptyEventForm(scope))
      await loadEventList()
    } catch (reason: unknown) {
      setError(errorMessage(reason, editingId === null ? 'Không thể tạo sự kiện.' : 'Không thể cập nhật sự kiện.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(event: LabEvent) {
    if (!token || !canManageEvent(event) || deletingId !== null) return
    if (!window.confirm(`Xóa mềm sự kiện “${event.title}”?`)) return
    setDeletingId(event.id)
    clearFeedback()
    try {
      await deleteEvent(token, event.id)
      if (editingId === event.id) {
        setEditingId(null)
        setForm(emptyEventForm(scope))
      }
      await loadEventList()
      setMessage(`Đã xóa mềm sự kiện “${event.title}”.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể xóa sự kiện.'))
    } finally {
      setDeletingId(null)
    }
  }

  function patchForm(next: Partial<EventForm>) {
    setForm((current) => ({ ...current, ...next }))
  }

  function clearFeedback() {
    setMessage('')
    setError('')
  }

  function canManageEvent(event: LabEvent) {
    return isAdmin || (isProjectLeader && event.projectId === project?.id && event.visibility === 'PROJECT')
  }

  return (
    <section className="page-section" aria-label="Quản lý sự kiện">
      <div className="panel-grid">
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>Sự kiện</h2>
              <p>Quản lý lịch Lab hoặc lịch gắn với dự án; không có danh sách người tham gia.</p>
            </div>
            <CalendarDays size={20} />
          </div>

          <Feedback message={message} error={error} />

          <div className="form-actions" style={{ marginBottom: 16 }}>
            <span className="badge info">{project ? `Dự án: ${project.code}` : 'Sự kiện cấp Lab'}</span>
            <select
              className="select dense"
              aria-label="Lọc trạng thái sự kiện"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as EventStatusFilter)}
            >
              <option value="ALL">Tất cả trạng thái</option>
              {EVENT_STATUSES.map((status) => <option value={status} key={status}>{EVENT_STATUS_LABELS[status]}</option>)}
            </select>
            <label className={`field-option ${upcomingOnly ? 'selected' : ''}`} style={{ paddingBlock: 8 }}>
              <input type="checkbox" checked={upcomingOnly} onChange={(event) => setUpcomingOnly(event.target.checked)} />
              <span><strong>Sắp diễn ra</strong></span>
            </label>
            <button
              className="btn ghost table-btn"
              type="button"
              disabled={loading}
              onClick={() => {
                setLoading(true)
                clearFeedback()
                void loadEventList()
                  .catch((reason: unknown) => setError(errorMessage(reason, 'Không tải lại được sự kiện.')))
                  .finally(() => setLoading(false))
              }}
            >
              <RefreshCw /> Tải lại
            </button>
          </div>

          {loading ? <div className="empty tight">Đang tải sự kiện...</div> : null}
          {!loading && events.length ? (
            <div className="field-admin-list">
              {events.map((event) => (
                <article className="field-admin-row" key={event.id}>
                  <div>
                    <strong>{event.title}</strong>
                    <span>{formatEventTime(event)}</span>
                    <span>{EVENT_MODE_LABELS[event.mode]} · {eventLocation(event)}</span>
                    <span className="inline-badges">
                      <span className={`badge ${EVENT_STATUS_BADGES[event.status]}`}>{EVENT_STATUS_LABELS[event.status]}</span>
                      <span className="badge info">{EVENT_VISIBILITY_LABELS[event.visibility]}</span>
                    </span>
                  </div>
                  {canManageEvent(event) ? (
                    <span className="field-admin-actions">
                      <button className="btn ghost table-btn" type="button" disabled={saving || deletingId !== null} onClick={() => beginEdit(event)}>
                        <Edit3 /> Sửa
                      </button>
                      <button className="btn ghost table-btn danger-text" type="button" disabled={saving || deletingId !== null} onClick={() => void handleDelete(event)}>
                        <Trash2 /> {deletingId === event.id ? 'Đang xóa...' : 'Xóa'}
                      </button>
                    </span>
                  ) : null}
                </article>
              ))}
            </div>
          ) : null}
          {!loading && !events.length ? (
            <EmptyState title="Chưa có sự kiện" description="Chưa có sự kiện phù hợp với phạm vi và bộ lọc hiện tại." />
          ) : null}
        </section>

        {canManage ? (
          <form className="panel" onSubmit={handleSubmit} noValidate>
            <div className="panel-head">
              <div>
                <h2>{editingId === null ? 'Tạo sự kiện' : 'Cập nhật sự kiện'}</h2>
                <p>{scope === 'PROJECT' ? `Gắn với dự án ${project?.code ?? ''}.` : 'Sự kiện cấp Lab, không gắn dự án.'}</p>
              </div>
              {editingId === null ? <Plus size={20} /> : <Edit3 size={20} />}
            </div>

            <div className="form-stack">
              <label className="field">
                <span>Tiêu đề</span>
                <input className="input" required maxLength={255} value={form.title} onChange={(event) => patchForm({ title: event.target.value })} />
              </label>
              <label className="field">
                <span>Nội dung</span>
                <textarea className="textarea" rows={4} maxLength={20_000} value={form.content} onChange={(event) => patchForm({ content: event.target.value })} />
              </label>
              <label className="field">
                <span>Hình thức</span>
                <select className="select" value={form.mode} onChange={(event) => patchForm({ mode: event.target.value as EventMode })}>
                  {EVENT_MODES.map((mode) => <option value={mode} key={mode}>{EVENT_MODE_LABELS[mode]}</option>)}
                </select>
              </label>
              {form.mode === 'IN_PERSON' ? (
                <label className="field">
                  <span>Địa điểm</span>
                  <input className="input" maxLength={255} value={form.location} onChange={(event) => patchForm({ location: event.target.value })} />
                </label>
              ) : (
                <label className="field">
                  <span>Link họp</span>
                  <input className="input" type="url" maxLength={2048} placeholder="https://..." value={form.meetingUrl} onChange={(event) => patchForm({ meetingUrl: event.target.value })} />
                </label>
              )}
              <label className="field">
                <span>Bắt đầu</span>
                <input className="input" type="datetime-local" required value={form.startAt} onChange={(event) => patchForm({ startAt: event.target.value })} />
              </label>
              <label className="field">
                <span>Kết thúc</span>
                <input className="input" type="datetime-local" value={form.endAt} onChange={(event) => patchForm({ endAt: event.target.value })} />
              </label>
              <label className="field">
                <span>Trạng thái</span>
                <select className="select" value={form.status} onChange={(event) => patchForm({ status: event.target.value as EventStatus })}>
                  {EVENT_STATUSES.map((status) => <option value={status} key={status}>{EVENT_STATUS_LABELS[status]}</option>)}
                </select>
              </label>
              <label className="field">
                <span>Phạm vi xem</span>
                <select className="select" value={form.visibility} onChange={(event) => patchForm({ visibility: event.target.value as EventVisibility })}>
                  {availableVisibilities.map((visibility) => (
                    <option value={visibility} key={visibility}>{EVENT_VISIBILITY_LABELS[visibility]}</option>
                  ))}
                </select>
              </label>
            </div>

            <div className="form-actions" style={{ marginTop: 16 }}>
              <button className="btn primary" type="submit" disabled={saving || deletingId !== null}>
                <Save /> {saving ? 'Đang lưu...' : editingId === null ? 'Tạo sự kiện' : 'Lưu sự kiện'}
              </button>
              {editingId !== null ? (
                <button className="btn ghost" type="button" disabled={saving} onClick={resetForm}>
                  <X /> Hủy sửa
                </button>
              ) : null}
            </div>
          </form>
        ) : (
          <section className="panel">
            <EmptyState
              title="Chỉ xem sự kiện"
              description="Admin quản lý sự kiện cấp Lab; Admin hoặc leader dự án quản lý sự kiện của dự án đang chọn."
            />
          </section>
        )}
      </div>
    </section>
  )
}

function emptyEventForm(scope: EventScope): EventForm {
  return {
    title: '',
    content: '',
    mode: 'IN_PERSON',
    location: '',
    meetingUrl: '',
    startAt: '',
    endAt: '',
    status: 'SCHEDULED',
    visibility: scope === 'PROJECT' ? 'PROJECT' : 'LAB',
  }
}

function toPayload(form: EventForm): Omit<CreateEventPayload, 'projectId'> {
  return {
    title: form.title.trim(),
    content: form.content.trim() || null,
    mode: form.mode,
    location: form.mode === 'IN_PERSON' ? form.location.trim() || null : null,
    meetingUrl: form.mode === 'ONLINE' ? form.meetingUrl.trim() || null : null,
    startAt: new Date(form.startAt).toISOString(),
    endAt: form.endAt ? new Date(form.endAt).toISOString() : null,
    status: form.status,
    visibility: form.visibility,
  }
}

function validateEventForm(form: EventForm) {
  if (!form.title.trim()) return 'Tiêu đề sự kiện là bắt buộc.'
  if (form.title.trim().length > 255) return 'Tiêu đề không được vượt quá 255 ký tự.'
  if (!form.startAt || Number.isNaN(new Date(form.startAt).getTime())) return 'Thời gian bắt đầu không hợp lệ.'
  if (form.endAt && Number.isNaN(new Date(form.endAt).getTime())) return 'Thời gian kết thúc không hợp lệ.'
  if (form.endAt && new Date(form.endAt) <= new Date(form.startAt)) return 'Thời gian kết thúc phải sau thời gian bắt đầu.'
  if (form.mode === 'IN_PERSON' && !form.location.trim()) return 'Sự kiện trực tiếp cần có địa điểm.'
  if (form.mode === 'ONLINE' && !isHttpUrl(form.meetingUrl)) return 'Sự kiện trực tuyến cần link họp HTTP/HTTPS hợp lệ.'
  return null
}

function isHttpUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function toDateTimeInput(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function formatEventTime(event: LabEvent) {
  const format = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
  const start = new Date(event.startAt)
  const end = event.endAt ? new Date(event.endAt) : null
  if (Number.isNaN(start.getTime())) return event.startAt
  return end && !Number.isNaN(end.getTime()) ? `${format.format(start)} – ${format.format(end)}` : format.format(start)
}

function eventLocation(event: LabEvent) {
  return event.mode === 'ONLINE' ? event.meetingUrl || 'Chưa có link họp' : event.location || 'Chưa có địa điểm'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
