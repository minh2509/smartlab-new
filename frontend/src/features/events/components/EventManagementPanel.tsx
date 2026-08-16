import {
  CalendarDays,
  Edit3,
  Eye,
  MapPin,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Trash2,
  Video,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
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
import './EventManagementPanel.css'

type EventScope = 'PROJECT' | 'LAB'
type EventStatusFilter = EventStatus | 'ALL'
type EventTimeFilter = 'ALL' | 'UPCOMING' | 'PAST'
type EventDialogMode = 'create' | 'edit' | 'view' | null

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

const EVENT_PAGE_SIZE = 10

export function EventManagementPanel({ project }: { project: Project | null }) {
  const { token, profile } = useAuth()
  const projectId = project?.id ?? null
  const scope: EventScope = project ? 'PROJECT' : 'LAB'
  const [events, setEvents] = useState<LabEvent[]>([])
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<EventStatusFilter>('ALL')
  const [timeFilter, setTimeFilter] = useState<EventTimeFilter>('ALL')
  const [eventPage, setEventPage] = useState(0)
  const [dialogMode, setDialogMode] = useState<EventDialogMode>(null)
  const [dialogEvent, setDialogEvent] = useState<LabEvent | null>(null)
  const [form, setForm] = useState<EventForm>(() => emptyEventForm(project ? 'PROJECT' : 'LAB'))
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [dialogError, setDialogError] = useState('')
  const dialogRef = useRef<HTMLDialogElement>(null)
  const dialogTriggerRef = useRef<HTMLButtonElement | null>(null)
  const listHeadingRef = useRef<HTMLHeadingElement>(null)
  const listRequestIdRef = useRef(0)
  const loadingOperationIdRef = useRef(0)

  const isAdmin = Boolean(
    profile?.roles.includes('ADMIN') && profile.permissions.includes('PROJECT_MANAGE'),
  )
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

  const filteredEvents = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('vi')
    if (!normalizedQuery) return events
    return events.filter((event) => [event.title, event.location, event.meetingUrl]
      .filter(Boolean)
      .some((value) => value!.toLocaleLowerCase('vi').includes(normalizedQuery)))
  }, [events, query])
  const eventPageCount = Math.max(1, Math.ceil(filteredEvents.length / EVENT_PAGE_SIZE))
  const pagedEvents = useMemo(
    () => filteredEvents.slice(
      eventPage * EVENT_PAGE_SIZE,
      (eventPage + 1) * EVENT_PAGE_SIZE,
    ),
    [eventPage, filteredEvents],
  )
  const hasFilters = Boolean(query.trim() || statusFilter !== 'ALL' || timeFilter !== 'ALL')
  const dialogDirty = useMemo(() => {
    if (dialogMode === 'create') return !sameEventForm(form, emptyEventForm(scope))
    if (dialogMode === 'edit' && dialogEvent) return !sameEventForm(form, toEventForm(dialogEvent))
    return false
  }, [dialogEvent, dialogMode, form, scope])
  const isBusy = saving || deletingId !== null

  const canManageEvent = useCallback((event: LabEvent) => (
    isAdmin
      || (isProjectLeader && event.projectId === project?.id && event.visibility === 'PROJECT')
  ), [isAdmin, isProjectLeader, project?.id])

  const loadEventList = useCallback(async (signal?: AbortSignal) => {
    if (!token) return
    const requestId = ++listRequestIdRef.current
    const result = await listEvents(token, {
      projectId: scope === 'PROJECT' ? project?.id : undefined,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      upcoming: timeFilter === 'ALL' ? undefined : timeFilter === 'UPCOMING',
    }, signal)
    if (signal?.aborted || requestId !== listRequestIdRef.current) return
    setEvents(scope === 'LAB' ? result.filter((event) => event.projectId === null) : result)
  }, [project?.id, scope, statusFilter, timeFilter, token])

  useEffect(() => {
    if (!token || (scope === 'PROJECT' && projectId === null)) {
      listRequestIdRef.current += 1
      loadingOperationIdRef.current += 1
      setLoading(false)
      setEvents([])
      return
    }
    let active = true
    const controller = new AbortController()
    const loadingOperationId = ++loadingOperationIdRef.current
    setLoading(true)
    setError('')
    void loadEventList(controller.signal)
      .catch((reason: unknown) => {
        if (active && !controller.signal.aborted) {
          setError(errorMessage(reason, 'Không tải được danh sách sự kiện.'))
        }
      })
      .finally(() => {
        if (active && loadingOperationId === loadingOperationIdRef.current) setLoading(false)
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [loadEventList, projectId, scope, token])

  useEffect(() => {
    if (dialogRef.current?.open) dialogRef.current.close()
    setDialogMode(null)
    setDialogEvent(null)
    setForm(emptyEventForm(projectId ? 'PROJECT' : 'LAB'))
    setDialogError('')
    setMessage('')
    setEventPage(0)
  }, [projectId])

  useEffect(() => {
    setEventPage(0)
  }, [query, statusFilter, timeFilter])

  useEffect(() => {
    setEventPage((current) => Math.min(current, eventPageCount - 1))
  }, [eventPageCount])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialogMode || !dialog || dialog.open) return
    dialog.showModal()
    window.requestAnimationFrame(() => {
      dialog.querySelector<HTMLElement>('[data-event-dialog-initial]')?.focus()
    })
  }, [dialogMode])

  function openCreateDialog(trigger: HTMLButtonElement) {
    if (!canManage || isBusy) return
    dialogTriggerRef.current = trigger
    setDialogEvent(null)
    setForm(emptyEventForm(scope))
    setDialogMode('create')
    setDialogError('')
  }

  function openEventDialog(event: LabEvent, trigger: HTMLButtonElement) {
    if (isBusy) return
    dialogTriggerRef.current = trigger
    setDialogEvent(event)
    setForm(toEventForm(event))
    setDialogMode(canManageEvent(event) ? 'edit' : 'view')
    setDialogError('')
  }

  function finishClosingDialog() {
    if (dialogRef.current?.open) dialogRef.current.close()
    setDialogMode(null)
    setDialogEvent(null)
    setForm(emptyEventForm(scope))
    setDialogError('')
    const trigger = dialogTriggerRef.current
    dialogTriggerRef.current = null
    window.requestAnimationFrame(() => {
      if (trigger?.isConnected) trigger.focus()
      else listHeadingRef.current?.focus()
    })
  }

  function requestCloseDialog() {
    if (isBusy) return
    if (dialogDirty && !window.confirm('Đóng cửa sổ và bỏ các thay đổi sự kiện chưa lưu?')) return
    finishClosingDialog()
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (
      !token
      || !canManage
      || isBusy
      || (scope === 'PROJECT' && !project)
      || (dialogMode !== 'create' && dialogMode !== 'edit')
    ) return

    const validationError = validateEventForm(form)
    if (validationError) {
      setDialogError(validationError)
      return
    }
    if (!availableVisibilities.includes(form.visibility)) {
      setDialogError('Phạm vi xem không hợp lệ với quyền hiện tại.')
      return
    }

    setSaving(true)
    setDialogError('')
    try {
      let saved: LabEvent
      if (dialogMode === 'create') {
        const payload: CreateEventPayload = {
          ...toPayload(form),
          projectId: scope === 'PROJECT' ? project!.id : null,
        }
        saved = await createEvent(token, payload)
        setEvents((current) => [saved, ...current.filter((item) => item.id !== saved.id)])
        setMessage(`Đã tạo sự kiện “${saved.title}”.`)
      } else {
        if (!dialogEvent || !canManageEvent(dialogEvent)) return
        const payload: UpdateEventPayload = toUpdatePayload(form, dialogEvent)
        saved = await updateEvent(token, dialogEvent.id, payload)
        setEvents((current) => current.map((item) => (item.id === saved.id ? saved : item)))
        setMessage(`Đã cập nhật sự kiện “${saved.title}”.`)
      }
      setError('')
      setSaving(false)
      finishClosingDialog()
      void loadEventList().catch((reason: unknown) => {
        setError(errorMessage(reason, 'Đã lưu nhưng chưa tải lại được danh sách sự kiện.'))
      })
    } catch (reason: unknown) {
      setDialogError(errorMessage(
        reason,
        dialogMode === 'create' ? 'Không thể tạo sự kiện.' : 'Không thể cập nhật sự kiện.',
      ))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (
      !token
      || !dialogEvent
      || !canManageEvent(dialogEvent)
      || isBusy
      || !window.confirm(`Xóa mềm sự kiện “${dialogEvent.title}”?`)
    ) return

    const deletedEvent = dialogEvent
    setDeletingId(deletedEvent.id)
    setDialogError('')
    try {
      await deleteEvent(token, deletedEvent.id)
      setEvents((current) => current.filter((item) => item.id !== deletedEvent.id))
      setMessage(`Đã xóa mềm sự kiện “${deletedEvent.title}”.`)
      setError('')
      setDeletingId(null)
      finishClosingDialog()
    } catch (reason: unknown) {
      setDialogError(errorMessage(reason, 'Không thể xóa sự kiện.'))
    } finally {
      setDeletingId(null)
    }
  }

  async function refreshEvents() {
    if (loading || isBusy) return
    const loadingOperationId = ++loadingOperationIdRef.current
    setLoading(true)
    setError('')
    try {
      await loadEventList()
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không tải lại được sự kiện.'))
    } finally {
      if (loadingOperationId === loadingOperationIdRef.current) setLoading(false)
    }
  }

  function resetFilters() {
    setQuery('')
    setStatusFilter('ALL')
    setTimeFilter('ALL')
    setEventPage(0)
  }

  function patchForm(next: Partial<EventForm>) {
    setForm((current) => ({ ...current, ...next }))
    setDialogError('')
  }

  return (
    <section className="page-section event-management" aria-label="Quản lý sự kiện">
      <section className="panel event-list-panel">
        <div className="panel-head event-list-head">
          <div>
            <h2 ref={listHeadingRef} tabIndex={-1}>Danh sách sự kiện</h2>
            <p>Lọc nhanh, sau đó mở từng sự kiện trong một cửa sổ riêng.</p>
          </div>
          {canManage ? (
            <button
              className="btn primary"
              type="button"
              disabled={loading || isBusy}
              onClick={(event) => openCreateDialog(event.currentTarget)}
            >
              <Plus aria-hidden="true" /> Tạo sự kiện
            </button>
          ) : <CalendarDays size={22} aria-hidden="true" />}
        </div>

        <Feedback message={message} error={error} />

        <div className="event-filter-grid">
          <label className="field event-search-field">
            <span>Tìm kiếm</span>
            <span className="input-with-icon">
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                placeholder="Tiêu đề, địa điểm hoặc link họp..."
                onChange={(event) => setQuery(event.target.value)}
              />
            </span>
          </label>
          <label className="field">
            <span>Trạng thái</span>
            <select
              className="select"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as EventStatusFilter)}
            >
              <option value="ALL">Tất cả trạng thái</option>
              {EVENT_STATUSES.map((status) => (
                <option value={status} key={status}>{EVENT_STATUS_LABELS[status]}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Thời gian</span>
            <select
              className="select"
              value={timeFilter}
              onChange={(event) => setTimeFilter(event.target.value as EventTimeFilter)}
            >
              <option value="ALL">Tất cả thời gian</option>
              <option value="UPCOMING">Sắp diễn ra</option>
              <option value="PAST">Đã bắt đầu</option>
            </select>
          </label>
          <div className="event-filter-actions">
            <button className="btn ghost table-btn" type="button" disabled={loading || isBusy} onClick={() => void refreshEvents()}>
              <RefreshCw aria-hidden="true" /> Tải lại
            </button>
            <button className="btn ghost table-btn" type="button" disabled={!hasFilters || loading || isBusy} onClick={resetFilters}>
              <RotateCcw aria-hidden="true" /> Đặt lại
            </button>
          </div>
        </div>

        <div className="event-list-summary">
          <span className="badge info">{project ? `Dự án: ${project.code}` : 'Sự kiện cấp Lab'}</span>
          <span>
            {filteredEvents.length} sự kiện
            {filteredEvents.length !== events.length ? ` trong ${events.length} kết quả đã tải` : ''}
          </span>
        </div>

        {loading ? <div className="empty tight">Đang tải sự kiện...</div> : null}
        {!loading && pagedEvents.length ? (
          <div className="field-admin-list event-list">
            {pagedEvents.map((event) => {
              const editable = canManageEvent(event)
              return (
                <article className="field-admin-row event-row" key={event.id}>
                  <span className="event-date-tile" aria-hidden="true">
                    <strong>{eventDay(event.startAt)}</strong>
                    <small>{eventMonth(event.startAt)}</small>
                  </span>
                  <div className="event-row-main">
                    <strong>{event.title}</strong>
                    <span>{formatEventTime(event)}</span>
                    <span className="event-row-place">
                      {event.mode === 'ONLINE' ? <Video aria-hidden="true" /> : <MapPin aria-hidden="true" />}
                      {EVENT_MODE_LABELS[event.mode]} · {eventLocation(event)}
                    </span>
                    <span className="inline-badges">
                      <span className={`badge ${EVENT_STATUS_BADGES[event.status]}`}>
                        {EVENT_STATUS_LABELS[event.status]}
                      </span>
                      <span className="badge info">{EVENT_VISIBILITY_LABELS[event.visibility]}</span>
                    </span>
                  </div>
                  <div className="event-row-actions">
                    <button
                      className="btn ghost table-btn"
                      type="button"
                      disabled={isBusy}
                      aria-label={`${editable ? 'Chỉnh sửa' : 'Xem'} sự kiện ${event.title}`}
                      onClick={(clickEvent) => openEventDialog(event, clickEvent.currentTarget)}
                    >
                      {editable ? <Edit3 aria-hidden="true" /> : <Eye aria-hidden="true" />}
                      {editable ? 'Chỉnh sửa' : 'Xem'}
                    </button>
                  </div>
                </article>
              )
            })}
          </div>
        ) : null}
        {!loading && !pagedEvents.length ? (
          <EmptyState
            title="Chưa có sự kiện"
            description={hasFilters
              ? 'Không có sự kiện phù hợp. Hãy thay đổi hoặc đặt lại bộ lọc.'
              : 'Chưa có sự kiện trong phạm vi đang chọn.'}
          />
        ) : null}

        {!loading && filteredEvents.length > EVENT_PAGE_SIZE ? (
          <nav className="event-pagination" aria-label="Phân trang sự kiện">
            <button
              className="btn ghost table-btn"
              type="button"
              disabled={eventPage === 0}
              onClick={() => setEventPage((current) => Math.max(0, current - 1))}
            >
              Trang trước
            </button>
            <span>Trang {eventPage + 1}/{eventPageCount}</span>
            <button
              className="btn ghost table-btn"
              type="button"
              disabled={eventPage >= eventPageCount - 1}
              onClick={() => setEventPage((current) => Math.min(eventPageCount - 1, current + 1))}
            >
              Trang sau
            </button>
          </nav>
        ) : null}
      </section>

      {dialogMode ? (
        <dialog
          ref={dialogRef}
          className="event-dialog"
          aria-labelledby="event-dialog-title"
          aria-describedby="event-dialog-description"
          onCancel={(event) => {
            event.preventDefault()
            requestCloseDialog()
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) requestCloseDialog()
          }}
        >
          <div className="event-dialog-shell">
            <header className="event-dialog-head">
              <div className="event-dialog-heading">
                <span className="event-dialog-icon" aria-hidden="true"><CalendarDays /></span>
                <div>
                  <span className="eyebrow">
                    {dialogMode === 'create' ? 'Tạo mới' : dialogMode === 'edit' ? 'Chỉnh sửa' : 'Chi tiết'}
                  </span>
                  <h2 id="event-dialog-title">
                    {dialogMode === 'create' ? 'Tạo sự kiện' : dialogEvent?.title}
                  </h2>
                  <p id="event-dialog-description">
                    {scope === 'PROJECT' ? `Thuộc dự án ${project?.code ?? ''}` : 'Sự kiện cấp Lab'}
                  </p>
                </div>
              </div>
              <button
                className="event-dialog-close"
                type="button"
                aria-label="Đóng cửa sổ sự kiện"
                disabled={isBusy}
                data-event-dialog-initial={dialogMode === 'view' ? true : undefined}
                onClick={requestCloseDialog}
              >
                <X aria-hidden="true" />
              </button>
            </header>

            <div className="event-dialog-body">
              <div role="alert" aria-live="assertive">
                <Feedback error={dialogError} />
              </div>

              {dialogMode === 'view' && dialogEvent ? (
                <EventDetails event={dialogEvent} project={project} />
              ) : (
                <form id="event-dialog-form" onSubmit={handleSubmit} noValidate>
                  <EventFormFields
                    form={form}
                    availableVisibilities={availableVisibilities}
                    disabled={isBusy}
                    onChange={patchForm}
                  />
                </form>
              )}

              {dialogMode === 'edit' && dialogEvent && canManageEvent(dialogEvent) ? (
                <div className="event-danger-zone">
                  <div>
                    <strong>Xóa sự kiện</strong>
                    <p>Sự kiện sẽ biến mất khỏi lịch đang hoạt động nhưng dữ liệu vẫn được giữ lại.</p>
                  </div>
                  <button className="btn danger-text" type="button" disabled={isBusy} onClick={() => void handleDelete()}>
                    <Trash2 aria-hidden="true" />
                    {deletingId === dialogEvent.id ? 'Đang xóa...' : 'Xóa sự kiện'}
                  </button>
                </div>
              ) : null}
            </div>

            <footer className="event-dialog-footer">
              <span className="muted small" aria-live="polite">
                {dialogMode === 'view'
                  ? 'Chế độ chỉ xem.'
                  : dialogDirty ? 'Có thay đổi chưa lưu.' : 'Chưa có thay đổi.'}
              </span>
              <div className="event-dialog-footer-actions">
                <button className="btn ghost" type="button" disabled={isBusy} onClick={requestCloseDialog}>
                  {dialogMode === 'view' ? 'Đóng' : 'Hủy'}
                </button>
                {dialogMode !== 'view' ? (
                  <button
                    className="btn primary"
                    type="submit"
                    form="event-dialog-form"
                    disabled={isBusy || (dialogMode === 'edit' && !dialogDirty)}
                  >
                    <Save aria-hidden="true" />
                    {saving ? 'Đang lưu...' : dialogMode === 'create' ? 'Tạo sự kiện' : 'Lưu thay đổi'}
                  </button>
                ) : null}
              </div>
            </footer>
          </div>
        </dialog>
      ) : null}
    </section>
  )
}

function EventFormFields({
  form,
  availableVisibilities,
  disabled,
  onChange,
}: {
  form: EventForm
  availableVisibilities: EventVisibility[]
  disabled: boolean
  onChange: (next: Partial<EventForm>) => void
}) {
  return (
    <div className="event-form-grid">
      <label className="field event-form-wide">
        <span>Tiêu đề</span>
        <input
          className="input"
          required
          maxLength={255}
          value={form.title}
          disabled={disabled}
          data-event-dialog-initial
          placeholder="Ví dụ: Seminar AI tháng 8"
          onChange={(event) => onChange({ title: event.target.value })}
        />
      </label>
      <label className="field event-form-wide">
        <span>Nội dung</span>
        <textarea
          className="textarea"
          rows={4}
          maxLength={20_000}
          value={form.content}
          disabled={disabled}
          placeholder="Mô tả nội dung và mục tiêu của sự kiện..."
          onChange={(event) => onChange({ content: event.target.value })}
        />
      </label>
      <label className="field">
        <span>Hình thức</span>
        <select
          className="select"
          value={form.mode}
          disabled={disabled}
          onChange={(event) => onChange({ mode: event.target.value as EventMode })}
        >
          {EVENT_MODES.map((mode) => (
            <option value={mode} key={mode}>{EVENT_MODE_LABELS[mode]}</option>
          ))}
        </select>
      </label>
      {form.mode === 'IN_PERSON' ? (
        <label className="field">
          <span>Địa điểm</span>
          <input
            className="input"
            maxLength={255}
            value={form.location}
            disabled={disabled}
            placeholder="Phòng hoặc địa điểm tổ chức"
            onChange={(event) => onChange({ location: event.target.value })}
          />
        </label>
      ) : (
        <label className="field">
          <span>Link họp</span>
          <input
            className="input"
            type="url"
            maxLength={2048}
            value={form.meetingUrl}
            disabled={disabled}
            placeholder="https://..."
            onChange={(event) => onChange({ meetingUrl: event.target.value })}
          />
        </label>
      )}
      <label className="field">
        <span>Bắt đầu</span>
        <input
          className="input"
          type="datetime-local"
          required
          value={form.startAt}
          disabled={disabled}
          onChange={(event) => onChange({ startAt: event.target.value })}
        />
      </label>
      <label className="field">
        <span>Kết thúc</span>
        <input
          className="input"
          type="datetime-local"
          value={form.endAt}
          disabled={disabled}
          onChange={(event) => onChange({ endAt: event.target.value })}
        />
      </label>
      <label className="field">
        <span>Trạng thái</span>
        <select
          className="select"
          value={form.status}
          disabled={disabled}
          onChange={(event) => onChange({ status: event.target.value as EventStatus })}
        >
          {EVENT_STATUSES.map((status) => (
            <option value={status} key={status}>{EVENT_STATUS_LABELS[status]}</option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Phạm vi xem</span>
        <select
          className="select"
          value={form.visibility}
          disabled={disabled}
          onChange={(event) => onChange({ visibility: event.target.value as EventVisibility })}
        >
          {availableVisibilities.map((visibility) => (
            <option value={visibility} key={visibility}>{EVENT_VISIBILITY_LABELS[visibility]}</option>
          ))}
        </select>
      </label>
    </div>
  )
}

function EventDetails({ event, project }: { event: LabEvent; project: Project | null }) {
  return (
    <div className="event-details-grid">
      <div className="event-detail event-detail-wide">
        <span>Nội dung</span>
        <strong className="event-detail-content">{event.content || 'Chưa có nội dung mô tả.'}</strong>
      </div>
      <div className="event-detail">
        <span>Thời gian</span>
        <strong>{formatEventTime(event)}</strong>
      </div>
      <div className="event-detail">
        <span>Hình thức</span>
        <strong>{EVENT_MODE_LABELS[event.mode]} · {eventLocation(event)}</strong>
      </div>
      <div className="event-detail">
        <span>Trạng thái</span>
        <strong>{EVENT_STATUS_LABELS[event.status]}</strong>
      </div>
      <div className="event-detail">
        <span>Phạm vi xem</span>
        <strong>{EVENT_VISIBILITY_LABELS[event.visibility]}</strong>
      </div>
      <div className="event-detail">
        <span>Thuộc phạm vi</span>
        <strong>{event.projectId ? project?.name ?? `Dự án #${event.projectId}` : 'Toàn Lab'}</strong>
      </div>
      <div className="event-detail">
        <span>Người tạo</span>
        <strong>{event.creator?.name ?? 'Tài khoản không còn tồn tại'}</strong>
      </div>
      <div className="event-detail">
        <span>Cập nhật gần nhất</span>
        <strong>{formatDateTime(event.updatedAt)}</strong>
      </div>
    </div>
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

function toEventForm(event: LabEvent): EventForm {
  return {
    title: event.title,
    content: event.content ?? '',
    mode: event.mode,
    location: event.location ?? '',
    meetingUrl: event.meetingUrl ?? '',
    startAt: toDateTimeInput(event.startAt),
    endAt: toDateTimeInput(event.endAt),
    status: event.status,
    visibility: event.visibility,
  }
}

function sameEventForm(left: EventForm, right: EventForm) {
  return left.title === right.title
    && left.content === right.content
    && left.mode === right.mode
    && left.location === right.location
    && left.meetingUrl === right.meetingUrl
    && left.startAt === right.startAt
    && left.endAt === right.endAt
    && left.status === right.status
    && left.visibility === right.visibility
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

function toUpdatePayload(form: EventForm, original: LabEvent): UpdateEventPayload {
  const baseline = toEventForm(original)
  const payload: UpdateEventPayload = {}

  if (form.title !== baseline.title) payload.title = form.title.trim()
  if (form.content !== baseline.content) payload.content = form.content.trim() || null
  if (form.mode !== baseline.mode) payload.mode = form.mode

  if (form.mode === 'IN_PERSON' && (form.mode !== baseline.mode || form.location !== baseline.location)) {
    payload.location = form.location.trim() || null
  }
  if (form.mode === 'ONLINE' && (form.mode !== baseline.mode || form.meetingUrl !== baseline.meetingUrl)) {
    payload.meetingUrl = form.meetingUrl.trim() || null
  }
  if (form.startAt !== baseline.startAt) payload.startAt = new Date(form.startAt).toISOString()
  if (form.endAt !== baseline.endAt) {
    payload.endAt = form.endAt ? new Date(form.endAt).toISOString() : null
  }
  if (form.status !== baseline.status) payload.status = form.status
  if (form.visibility !== baseline.visibility) payload.visibility = form.visibility

  return payload
}

function validateEventForm(form: EventForm) {
  if (!form.title.trim()) return 'Tiêu đề sự kiện là bắt buộc.'
  if (form.title.trim().length > 255) return 'Tiêu đề không được vượt quá 255 ký tự.'
  if (form.content.trim().length > 20_000) return 'Nội dung không được vượt quá 20.000 ký tự.'
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
  return end && !Number.isNaN(end.getTime())
    ? `${format.format(start)} – ${format.format(end)}`
    : format.format(start)
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function eventDay(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '--' : new Intl.DateTimeFormat('vi-VN', { day: '2-digit' }).format(date)
}

function eventMonth(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '---' : new Intl.DateTimeFormat('vi-VN', { month: 'short' }).format(date)
}

function eventLocation(event: LabEvent) {
  return event.mode === 'ONLINE'
    ? event.meetingUrl || 'Chưa có link họp'
    : event.location || 'Chưa có địa điểm'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
