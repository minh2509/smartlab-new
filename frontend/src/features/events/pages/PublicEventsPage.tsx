import {
  ArrowRight,
  CalendarDays,
  Clock3,
  ExternalLink,
  MapPin,
  RotateCcw,
  Search,
  UserRound,
  Video,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Feedback } from '../../../shared/components/Feedback'
import { EmptyState } from '../../../shared/components/EmptyState'
import { closeOverlayPopups } from '../../../shared/ui/overlayPortalStore'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { PublicPageHead } from '../../public/components/PublicPageHead'
import { listPublicEvents } from '../api'
import {
  EVENT_MODE_LABELS,
  EVENT_STATUSES,
  EVENT_STATUS_BADGES,
  EVENT_STATUS_LABELS,
  EVENT_VISIBILITY_LABELS,
  type EventStatus,
  type LabEvent,
} from '../types'
import './PublicEventsPage.css'

type TimeFilter = 'ALL' | 'UPCOMING' | 'PAST'
type StatusFilter = EventStatus | 'ALL'

const timeOptions = [
  { value: 'ALL', label: 'Tất cả thời gian' },
  { value: 'UPCOMING', label: 'Sắp diễn ra' },
  { value: 'PAST', label: 'Đã diễn ra' },
]

const statusOptions = [
  { value: 'ALL', label: 'Tất cả trạng thái' },
  ...EVENT_STATUSES.map((value) => ({ value, label: EVENT_STATUS_LABELS[value] })),
]

export function PublicEventsPage() {
  const [events, setEvents] = useState<LabEvent[] | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [query, setQuery] = useState('')
  const [timeFilter, setTimeFilter] = useState<TimeFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [selectedEvent, setSelectedEvent] = useState<LabEvent | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)
  const dialogTriggerRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setLoadError(false)
    void listPublicEvents({}, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setEvents(result) })
      .catch(() => { if (!controller.signal.aborted) setLoadError(true) })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!selectedEvent || !dialog || dialog.open) return
    closeOverlayPopups()
    dialog.showModal()
    window.requestAnimationFrame(() => dialog.querySelector<HTMLElement>('[data-public-event-dialog-initial]')?.focus())
  }, [selectedEvent])

  const filteredEvents = useMemo(() => {
    if (!events) return []
    const normalizedQuery = query.trim().toLocaleLowerCase('vi')
    const now = Date.now()
    return events.filter((event) => {
      const startAt = eventTimestamp(event.startAt)
      const matchesQuery = !normalizedQuery || [event.title, event.content, event.location, event.meetingUrl]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLocaleLowerCase('vi').includes(normalizedQuery))
      const matchesTime = timeFilter === 'ALL'
        || (Number.isFinite(startAt) && (timeFilter === 'UPCOMING' ? startAt >= now : startAt < now))
      return matchesQuery && matchesTime && (statusFilter === 'ALL' || event.status === statusFilter)
    })
  }, [events, query, statusFilter, timeFilter])

  const hasFilters = Boolean(query.trim() || timeFilter !== 'ALL' || statusFilter !== 'ALL')
  const isLoading = events === null && !loadError

  function resetFilters() {
    setQuery('')
    setTimeFilter('ALL')
    setStatusFilter('ALL')
  }

  function openEvent(event: LabEvent, trigger: HTMLButtonElement) {
    dialogTriggerRef.current = trigger
    setSelectedEvent(event)
  }

  function closeDetail() {
    dialogRef.current?.close()
  }

  function finishClosingDetail() {
    setSelectedEvent(null)
    const trigger = dialogTriggerRef.current
    dialogTriggerRef.current = null
    window.requestAnimationFrame(() => {
      if (trigger?.isConnected) trigger.focus()
    })
  }

  return (
    <>
      <PublicPageHead
        title="Sự kiện"
        description="Workshop, demo, seminar và các buổi đánh giá tiến độ của Smart Lab."
      />
      <section className="section public-events-section">
        <div className="wrap public-events-wrap">
          {isLoading ? <EventLoadingState /> : null}
          {loadError ? <Feedback error="Không tải được danh sách sự kiện. Vui lòng thử lại sau." /> : null}
          {events ? <>
            <div className="public-events-toolbar" role="search">
              <label className="public-events-search">
                <span className="sr-only">Tìm kiếm sự kiện</span>
                <Search size={18} aria-hidden="true" />
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo tên, nội dung hoặc địa điểm..." />
              </label>
              <div className="public-events-filter">
                <PopupSelect value={timeFilter} options={timeOptions} onChange={(value) => setTimeFilter(value as TimeFilter)} ariaLabel="Lọc theo thời gian" />
              </div>
              <div className="public-events-filter">
                <PopupSelect value={statusFilter} options={statusOptions} onChange={(value) => setStatusFilter(value as StatusFilter)} ariaLabel="Lọc theo trạng thái" />
              </div>
              {hasFilters ? <button className="btn ghost sm public-events-reset" type="button" onClick={resetFilters}><RotateCcw size={15} aria-hidden="true" /> Đặt lại</button> : null}
            </div>

            {events.length === 0 ? <EmptyState title="Chưa có sự kiện" description="Các hoạt động mới của Smart Lab sẽ xuất hiện tại đây." /> : <>
              <p className="public-events-summary" aria-live="polite">
                {hasFilters ? `${filteredEvents.length} kết quả` : `${events.length} sự kiện`}
              </p>
              {filteredEvents.length ? <div className="event-directory-list">
                {filteredEvents.map((event) => <EventRow key={event.id} event={event} onOpen={openEvent} />)}
              </div> : <div className="public-events-filter-empty">
                <EmptyState title="Không tìm thấy sự kiện phù hợp" description="Thử thay đổi từ khóa hoặc bộ lọc." />
                <button className="btn ghost sm" type="button" onClick={resetFilters}><RotateCcw size={15} aria-hidden="true" /> Đặt lại</button>
              </div>}
            </>}
          </> : null}
        </div>
      </section>

      {selectedEvent ? <dialog
        ref={dialogRef}
        className="public-event-dialog"
        aria-labelledby="public-event-dialog-title"
        onCancel={(event) => { event.preventDefault(); closeDetail() }}
        onClose={finishClosingDetail}
        onClick={(event) => { if (event.target === event.currentTarget) closeDetail() }}
      >
        <article className="public-event-dialog-shell">
          <header className="public-event-dialog-head">
            <div>
              <p>Chi tiết sự kiện</p>
              <h2 id="public-event-dialog-title">{selectedEvent.title}</h2>
            </div>
            <div className="public-event-dialog-actions">
              <StatusBadge event={selectedEvent} />
              <button className="icon-btn public-event-dialog-close" type="button" onClick={closeDetail} aria-label="Đóng chi tiết sự kiện" data-public-event-dialog-initial>
                <X aria-hidden="true" />
              </button>
            </div>
          </header>
          <div className="public-event-dialog-body">
            <EventDetails event={selectedEvent} />
          </div>
          <footer className="public-event-dialog-footer">
            <button className="btn ghost" type="button" onClick={closeDetail}>Đóng</button>
          </footer>
        </article>
      </dialog> : null}
    </>
  )
}

function EventLoadingState() {
  return <div className="event-directory-list" aria-label="Đang tải sự kiện" aria-busy="true">
    {[1, 2, 3].map((item) => <div className="event-directory-skeleton" key={item}><span /><div><b /><i /></div><em /></div>)}
  </div>
}

function EventRow({ event, onOpen }: { event: LabEvent; onOpen: (event: LabEvent, trigger: HTMLButtonElement) => void }) {
  const start = eventTimestamp(event.startAt)
  const end = eventTimestamp(event.endAt)
  const hasMeaningfulEnd = Number.isFinite(end) && (!Number.isFinite(start) || end > start)
  const location = event.mode === 'IN_PERSON' ? event.location?.trim() : null

  return <button className="event-directory-row" type="button" onClick={(click) => onOpen(event, click.currentTarget)}>
    <EventDateTile timestamp={start} />
    <span className="event-directory-content">
      <strong>{event.title}</strong>
      <span className="event-directory-meta">
        <span><CalendarDays aria-hidden="true" />{formatEventDateTime(event.startAt)}</span>
        {hasMeaningfulEnd ? <span><Clock3 aria-hidden="true" />Kết thúc {formatEventDateTime(event.endAt)}</span> : null}
      </span>
      <span className="event-directory-meta event-directory-meta-secondary">
        <span>{event.mode === 'ONLINE' ? <Video aria-hidden="true" /> : <MapPin aria-hidden="true" />}{EVENT_MODE_LABELS[event.mode]}{location ? ` · ${location}` : ''}</span>
        <span>{EVENT_VISIBILITY_LABELS[event.visibility]}</span>
      </span>
      {event.content?.trim() ? <span className="event-directory-preview">{event.content.trim()}</span> : null}
    </span>
    <span className="event-directory-side">
      <StatusBadge event={event} />
      <span className="event-directory-detail">Xem chi tiết <ArrowRight size={16} aria-hidden="true" /></span>
    </span>
  </button>
}

function EventDateTile({ timestamp }: { timestamp: number }) {
  if (!Number.isFinite(timestamp)) return <span className="event-directory-date"><b>--</b><small>---</small></span>
  const date = new Date(timestamp)
  return <span className="event-directory-date"><b>{new Intl.DateTimeFormat('vi-VN', { day: '2-digit' }).format(date)}</b><small>{new Intl.DateTimeFormat('vi-VN', { month: 'short' }).format(date)}</small></span>
}

function StatusBadge({ event }: { event: LabEvent }) {
  return <span className={`badge ${EVENT_STATUS_BADGES[event.status]}`}>{EVENT_STATUS_LABELS[event.status]}</span>
}

function EventDetails({ event }: { event: LabEvent }) {
  const start = eventTimestamp(event.startAt)
  const end = eventTimestamp(event.endAt)
  const hasMeaningfulEnd = Number.isFinite(end) && (!Number.isFinite(start) || end > start)
  const location = event.mode === 'IN_PERSON' ? event.location?.trim() : null
  const creatorName = event.creator?.name?.trim()

  return <div className="public-event-details">
    <section>
      <h3>Thời gian</h3>
      <dl className="public-event-detail-grid">
        <div><dt><CalendarDays aria-hidden="true" />Bắt đầu</dt><dd>{formatEventDateTime(event.startAt)}</dd></div>
        {hasMeaningfulEnd ? <div><dt><Clock3 aria-hidden="true" />Kết thúc</dt><dd>{formatEventDateTime(event.endAt)}</dd></div> : null}
      </dl>
    </section>
    <section>
      <h3>Hình thức</h3>
      <dl className="public-event-detail-grid">
        <div><dt>{event.mode === 'ONLINE' ? <Video aria-hidden="true" /> : <MapPin aria-hidden="true" />}Hình thức</dt><dd>{EVENT_MODE_LABELS[event.mode]}</dd></div>
        {location ? <div><dt><MapPin aria-hidden="true" />Địa điểm</dt><dd>{location}</dd></div> : null}
        <div><dt>Phạm vi</dt><dd>{EVENT_VISIBILITY_LABELS[event.visibility]}</dd></div>
      </dl>
      {event.mode === 'ONLINE' && event.meetingUrl?.trim() ? <a className="btn brand sm public-event-meeting-link" href={event.meetingUrl} target="_blank" rel="noreferrer"><Video aria-hidden="true" /> Tham gia trực tuyến <ExternalLink size={15} aria-hidden="true" /></a> : null}
    </section>
    {event.content?.trim() ? <section><h3>Nội dung</h3><p className="public-event-detail-content">{event.content.trim()}</p></section> : null}
    {creatorName ? <section><h3>Thông tin</h3><dl className="public-event-detail-grid"><div><dt><UserRound aria-hidden="true" />Người tạo</dt><dd>{creatorName}</dd></div></dl></section> : null}
  </div>
}

function eventTimestamp(value: string | null): number {
  if (!value) return Number.NaN
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? Number.NaN : timestamp
}

function formatEventDateTime(value: string | null): string {
  const timestamp = eventTimestamp(value)
  if (!Number.isFinite(timestamp)) return 'Chưa cập nhật'
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(timestamp))
}
