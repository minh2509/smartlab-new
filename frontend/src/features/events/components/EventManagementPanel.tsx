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
import { useToast } from '../../../shared/toast/useToast'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { OverlayPortalHost } from '../../../shared/ui/OverlayPortalHost'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
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
  const toast = useToast()
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
    if (dialogMode === 'edit' && dialogEvent) {
      return Object.keys(toUpdatePayload(form, dialogEvent)).length > 0
    }
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
    setDialogMode('view')
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

  async function requestCloseDialog() {
    if (isBusy) return
    if (dialogDirty && !await confirmDialog({
      title: 'Bỏ thay đổi?',
      description: 'Các thay đổi chưa lưu của sự kiện sẽ bị mất.',
      cancelLabel: 'Tiếp tục chỉnh sửa',
      confirmLabel: 'Bỏ thay đổi',
    })) return
    finishClosingDialog()
  }

  function startEditing() {
    if (!dialogEvent || !canManageEvent(dialogEvent) || isBusy) return
    setDialogMode('edit')
    window.requestAnimationFrame(() => {
      dialogRef.current?.querySelector<HTMLElement>('[data-event-dialog-initial]')?.focus()
    })
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
        toast.success('Đã tạo sự kiện', `“${saved.title}” đã được thêm vào lịch.`)
      } else {
        if (!dialogEvent || !canManageEvent(dialogEvent)) return
        const payload: UpdateEventPayload = toUpdatePayload(form, dialogEvent)
        saved = await updateEvent(token, dialogEvent.id, payload)
        setEvents((current) => current.map((item) => (item.id === saved.id ? saved : item)))
        toast.success('Đã cập nhật sự kiện', `“${saved.title}” đã được lưu.`)
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
    ) return
    if (!await confirmDialog({
      title: 'Xóa sự kiện?',
      description: 'Sự kiện sẽ không còn xuất hiện trong lịch hoạt động. Dữ liệu lịch sử vẫn được giữ lại.',
      confirmLabel: 'Xóa sự kiện',
      destructive: true,
    })) return

    const deletedEvent = dialogEvent
    setDeletingId(deletedEvent.id)
    setDialogError('')
    try {
      await deleteEvent(token, deletedEvent.id)
      setEvents((current) => current.filter((item) => item.id !== deletedEvent.id))
      toast.success('Đã xóa mềm sự kiện', `“${deletedEvent.title}” đã được gỡ khỏi lịch hoạt động.`)
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
            <div className="event-list-title-row">
              <h2 ref={listHeadingRef} tabIndex={-1}>Danh sách sự kiện</h2>
              <span className="event-list-count">{filteredEvents.length} sự kiện</span>
            </div>
            <p>Tìm, lọc và xem chi tiết các hoạt động.</p>
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

        <Feedback error={error} />

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
          <div className="field">
            <span>Trạng thái</span>
            <PopupSelect
              value={statusFilter}
              onChange={(value) => setStatusFilter(value as EventStatusFilter)}
              ariaLabel="Lọc theo trạng thái sự kiện"
              options={[{ value: 'ALL', label: 'Tất cả trạng thái' }, ...EVENT_STATUSES.map((status) => ({ value: status, label: EVENT_STATUS_LABELS[status] }))]}
            />
          </div>
          <div className="field">
            <span>Thời gian</span>
            <PopupSelect
              value={timeFilter}
              onChange={(value) => setTimeFilter(value as EventTimeFilter)}
              ariaLabel="Lọc theo thời gian sự kiện"
              options={[{ value: 'ALL', label: 'Tất cả thời gian' }, { value: 'UPCOMING', label: 'Sắp diễn ra' }, { value: 'PAST', label: 'Đã bắt đầu' }]}
            />
          </div>
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
          <span>
            {project ? `Trong phạm vi ${project.code}. ` : 'Trong phạm vi cấp Lab. '}
            {filteredEvents.length !== events.length ? `Đang hiển thị ${filteredEvents.length} trong ${events.length} kết quả đã tải.` : ''}
          </span>
        </div>

        {loading ? <div className="empty tight">Đang tải sự kiện...</div> : null}
        {!loading && pagedEvents.length ? (
          <div className="field-admin-list event-list">
            {pagedEvents.map((event) => {
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
                      aria-label={`Chi tiết sự kiện ${event.title}`}
                      title={`Chi tiết sự kiện ${event.title}`}
                      onClick={(clickEvent) => openEventDialog(event, clickEvent.currentTarget)}
                    >
                      <Eye aria-hidden="true" />
                      Chi tiết
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
            void requestCloseDialog()
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) void requestCloseDialog()
          }}
        >
          <OverlayPortalHost />
          <div className="event-dialog-shell">
            <header className="event-dialog-head">
              <div className="event-dialog-heading">
                <div>
                  <span className="event-dialog-kicker">
                    {dialogMode === 'create' ? 'Tạo sự kiện' : dialogMode === 'edit' ? 'Chỉnh sửa sự kiện' : 'Chi tiết sự kiện'}
                  </span>
                  <h2 id="event-dialog-title">
                    {dialogMode === 'create' ? 'Tạo sự kiện' : dialogEvent?.title}
                  </h2>
                  <p id="event-dialog-description">
                    {scope === 'PROJECT' ? `${project?.code ?? ''} · ${project?.name ?? ''}` : 'Sự kiện cấp Lab'}
                  </p>
                </div>
              </div>
              <div className="event-dialog-header-actions">
                {dialogMode === 'view' && dialogEvent && canManageEvent(dialogEvent) ? (
                  <button className="btn ghost table-btn" type="button" onClick={startEditing} disabled={isBusy}>
                    <Edit3 aria-hidden="true" /> Chỉnh sửa
                  </button>
                ) : null}
                {dialogMode === 'view' && dialogEvent ? <span className={`badge ${EVENT_STATUS_BADGES[dialogEvent.status]}`}>{EVENT_STATUS_LABELS[dialogEvent.status]}</span> : null}
                <button
                  className="event-dialog-close"
                  type="button"
                  aria-label="Đóng cửa sổ sự kiện"
                  disabled={isBusy}
                  data-event-dialog-initial={dialogMode === 'view' ? true : undefined}
                  onClick={() => void requestCloseDialog()}
                >
                  <X aria-hidden="true" />
                </button>
              </div>
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
                    <p>Sự kiện sẽ không còn xuất hiện trong lịch hoạt động. Dữ liệu lịch sử vẫn được giữ lại.</p>
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
                  ? ''
                  : dialogDirty ? 'Có thay đổi chưa lưu.' : 'Chưa có thay đổi.'}
              </span>
              <div className="event-dialog-footer-actions">
                <button className="btn ghost" type="button" disabled={isBusy} onClick={() => void requestCloseDialog()}>
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
    <div className="event-form-layout">
      <section className="event-form-section">
        <h3>Thông tin sự kiện</h3>
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
        </div>
      </section>
      <section className="event-form-section">
        <h3>Tổ chức</h3>
        <div className="event-form-grid">
      <label className="field">
        <span>Hình thức</span>
        <PopupSelect
          value={form.mode}
          disabled={disabled}
          ariaLabel="Hình thức sự kiện"
          onChange={(value) => onChange({ mode: value as EventMode })}
          options={EVENT_MODES.map((mode) => ({ value: mode, label: EVENT_MODE_LABELS[mode] }))}
        />
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
        </div>
      </section>
      <section className="event-form-section">
        <h3>Thời gian</h3>
        <div className="event-form-grid">
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
        </div>
      </section>
      <section className="event-form-section">
        <h3>Phân loại</h3>
        <div className="event-form-grid">
      <label className="field">
        <span>Trạng thái</span>
        <PopupSelect
          value={form.status}
          disabled={disabled}
          ariaLabel="Trạng thái sự kiện"
          onChange={(value) => onChange({ status: value as EventStatus })}
          options={EVENT_STATUSES.map((status) => ({ value: status, label: EVENT_STATUS_LABELS[status] }))}
        />
      </label>
      <label className="field">
        <span>Phạm vi xem</span>
        <PopupSelect
          value={form.visibility}
          disabled={disabled}
          ariaLabel="Phạm vi xem sự kiện"
          onChange={(value) => onChange({ visibility: value as EventVisibility })}
          options={availableVisibilities.map((visibility) => ({ value: visibility, label: EVENT_VISIBILITY_LABELS[visibility] }))}
        />
      </label>
        </div>
      </section>
    </div>
  )
}

function EventDetails({ event, project }: { event: LabEvent; project: Project | null }) {
  return (
    <div className="event-details">
      <section className="event-detail-section">
        <h3>Thông tin</h3>
        <div className="event-detail-content">{event.content || 'Chưa có nội dung mô tả.'}</div>
      </section>
      <section className="event-detail-section">
        <h3>Thời gian & tổ chức</h3>
        <div className="event-details-grid">
          <DetailItem label="Thời gian" value={formatEventTime(event)} />
          <DetailItem label="Hình thức" value={`${EVENT_MODE_LABELS[event.mode]} · ${eventLocation(event)}`} />
        </div>
      </section>
      <section className="event-detail-section">
        <h3>Phạm vi & trạng thái</h3>
        <div className="event-details-grid event-details-grid-three">
          <DetailItem label="Trạng thái" value={EVENT_STATUS_LABELS[event.status]} />
          <DetailItem label="Phạm vi xem" value={EVENT_VISIBILITY_LABELS[event.visibility]} />
          <DetailItem label="Thuộc phạm vi" value={event.projectId ? project?.name ?? `Dự án #${event.projectId}` : 'Toàn Lab'} />
        </div>
      </section>
      <section className="event-detail-section">
        <h3>Metadata</h3>
        <div className="event-details-grid">
          <DetailItem label="Người tạo" value={event.creator?.name ?? 'Tài khoản không còn tồn tại'} />
          <DetailItem label="Cập nhật gần nhất" value={formatDateTime(event.updatedAt)} />
        </div>
      </section>
    </div>
  )
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return <div className="event-detail"><span>{label}</span><strong>{value}</strong></div>
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
