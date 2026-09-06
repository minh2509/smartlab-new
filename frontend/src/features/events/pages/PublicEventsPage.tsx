import { Pagination } from "../../../shared/components/Pagination";

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
import { type KeyboardEvent, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Feedback } from '../../../shared/components/Feedback'
import { EmptyState } from '../../../shared/components/EmptyState'
import { closeOverlayPopups } from '../../../shared/ui/overlayPortalStore'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { PublicPageHead } from '../../public/components/PublicPageHead'
import { getPublicEvent, listPublicEventArchive } from '../api'
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

const PAGE_SIZE = 12
type TimeFilter = 'ALL' | 'UPCOMING' | 'PAST'
type FilterValue = 'ALL' | string

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
  const [searchParams, setSearchParams] = useSearchParams()
  const [pageData, setPageData] = useState<{ items: LabEvent[]; page: number; size: number; totalElements: number; totalPages: number } | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selectedEvent, setSelectedEvent] = useState<LabEvent | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)
  const dialogTriggerRef = useRef<HTMLButtonElement | null>(null)
  const detailRequestRef = useRef<{ controller: AbortController; requestId: number } | null>(null)
  const detailRequestIdRef = useRef(0)
  const closeInProgressRef = useRef(false)

  useEffect(() => {
    return () => {
      detailRequestIdRef.current += 1
      detailRequestRef.current?.controller.abort()
      detailRequestRef.current = null
    }
  }, [])

  const currentPage = parsePage(searchParams.get('page'))
  const query = searchParams.get('q') ?? ''
  const timeFilter = asTimeFilter(searchParams.get('time'))
  const statusFilter = asEventStatus(searchParams.get('status'))
  const events = pageData?.items ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0
  const hasFilters = Boolean(query.trim() || timeFilter !== 'ALL' || statusFilter !== 'ALL')

  useEffect(() => {
    const controller = new AbortController()
    setPageData(null)
    setLoadError(null)
    void listPublicEventArchive(currentPage - 1, PAGE_SIZE, {
      query,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      upcoming: timeFilter === 'UPCOMING' ? true : timeFilter === 'PAST' ? false : undefined,
    }, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setPageData(result)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setLoadError(reason instanceof Error ? reason.message : 'Không tải được danh sách sự kiện.')
      })
    return () => controller.abort()
  }, [currentPage, query, statusFilter, timeFilter])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!selectedEvent || !dialog || dialog.open) return
    closeOverlayPopups()
    dialog.showModal()
    window.requestAnimationFrame(() => dialog.querySelector<HTMLElement>('[data-public-event-dialog-initial]')?.focus())
  }, [selectedEvent])

  function updateFilter(key: string, value: FilterValue) {
    const next = new URLSearchParams(searchParams)
    if (value === 'ALL' || value === '') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearchParams(next)
  }

  function resetFilters() {
    setSearchParams(new URLSearchParams())
  }

  function updatePage(page: number) {
    const next = new URLSearchParams(searchParams)
    if (page <= 1) next.delete('page')
    else next.set('page', String(page))
    setSearchParams(next)
  }

  function openEvent(event: LabEvent, trigger: HTMLButtonElement) {
    detailRequestRef.current?.controller.abort()
    const requestId = detailRequestIdRef.current + 1
    detailRequestIdRef.current = requestId
    const controller = new AbortController()
    detailRequestRef.current = { controller, requestId }

    dialogTriggerRef.current = trigger
    setDetailError(null)
    setDetailLoading(true)
    setSelectedEvent(event)

    void getPublicEvent(event.id, controller.signal)
      .then((result) => {
        if (controller.signal.aborted || detailRequestRef.current?.requestId !== requestId) return
        setSelectedEvent(result)
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted || detailRequestRef.current?.requestId !== requestId) return
        setDetailError(reason instanceof Error ? reason.message : 'Không tải được chi tiết sự kiện.')
      })
      .finally(() => {
        if (controller.signal.aborted || detailRequestRef.current?.requestId !== requestId) return
        detailRequestRef.current = null
        setDetailLoading(false)
      })
  }

  function closeDetail() {
    if (closeInProgressRef.current) return
    closeInProgressRef.current = true
    detailRequestIdRef.current += 1
    detailRequestRef.current?.controller.abort()
    detailRequestRef.current = null
    dialogRef.current?.close()
  }

  function handleDialogKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key !== 'Escape') return
    event.preventDefault()
    event.stopPropagation()
    closeDetail()
  }

  function finishClosingDetail() {
    closeInProgressRef.current = false
    setSelectedEvent(null)
    setDetailError(null)
    setDetailLoading(false)
    const trigger = dialogTriggerRef.current
    dialogTriggerRef.current = null
    window.requestAnimationFrame(() => {
      if (trigger?.isConnected) trigger.focus()
    })
  }

  return (
    <>
      <PublicPageHead title="Sự kiện" description="Workshop, demo, seminar và các hoạt động cộng đồng của Smart Lab." />
      <section className="section public-events-section">
        <div className="wrap public-events-wrap">
          <div className="public-events-intro">
            <div>
              <div className="kicker">LAB ACTIVITIES</div>
              <h2>Hoạt động của Lab</h2>
              <p>Workshop, giao lưu, outing và những hoạt động giúp cộng đồng Smart Lab học hỏi và kết nối.</p>
            </div>
            <span className="public-events-page-size">12 sự kiện / trang</span>
          </div>

          <div className="public-events-toolbar" role="search">
            <label className="public-events-search">
              <span className="sr-only">Tìm kiếm sự kiện</span>
              <Search size={18} aria-hidden="true" />
              <input value={query} onChange={(event) => updateFilter('q', event.target.value)} placeholder="Tìm theo tên, nội dung hoặc địa điểm..." />
            </label>
            <div className="public-events-filter">
              <PopupSelect value={timeFilter} options={timeOptions} onChange={(value) => updateFilter('time', value)} ariaLabel="Lọc theo thời gian" />
            </div>
            <div className="public-events-filter">
              <PopupSelect value={statusFilter} options={statusOptions} onChange={(value) => updateFilter('status', value)} ariaLabel="Lọc theo trạng thái" />
            </div>
            {hasFilters ? <button className="btn ghost sm public-events-reset" type="button" onClick={resetFilters}><RotateCcw size={15} aria-hidden="true" /> Đặt lại</button> : null}
          </div>

          {loadError ? <div className="public-events-request-state" role="alert"><Feedback error={loadError} /><button className="btn" type="button" onClick={() => setSearchParams(new URLSearchParams(searchParams))}>Thử tải lại</button></div> : null}
          {!pageData && !loadError ? <EventLoadingState /> : null}

          {pageData && events.length === 0 ? (
            <EmptyState title={hasFilters ? 'Không tìm thấy hoạt động phù hợp' : 'Chưa có hoạt động công khai'} description={hasFilters ? 'Thử thay đổi từ khóa hoặc bộ lọc.' : 'Các hoạt động mới của Smart Lab sẽ xuất hiện tại đây.'} />
          ) : null}

          {pageData && events.length > 0 ? (
            <>
              <div className="public-events-results-bar">
                <p className="public-events-summary" aria-live="polite">{formatRange(currentPage, PAGE_SIZE, totalElements)} · {totalElements} sự kiện</p>
              </div>
              <div className="event-directory-list" aria-busy="false">
                {events.map((event) => <EventRow key={event.id} event={event} onOpen={openEvent} />)}
              </div>
              <Pagination page={currentPage} totalPages={totalPages} onChange={updatePage} />
            </>
          ) : null}
        </div>
      </section>

      {selectedEvent ? <dialog
        ref={dialogRef}
        className="public-event-dialog"
        aria-labelledby="public-event-dialog-title"
        onKeyDown={handleDialogKeyDown}
        onCancel={(event) => { event.preventDefault(); closeDetail() }}
        onClose={finishClosingDetail}
        onClick={(event) => { if (event.target === event.currentTarget) closeDetail() }}
      >
        <article className="public-event-dialog-shell">
          <header className="public-event-dialog-head">
            <div><p>Chi tiết hoạt động</p><h2 id="public-event-dialog-title">{selectedEvent.title}</h2></div>
            <div className="public-event-dialog-actions">
              <StatusBadge event={selectedEvent} />
              <button className="icon-btn public-event-dialog-close" type="button" onClick={closeDetail} aria-label="Đóng chi tiết sự kiện" data-public-event-dialog-initial><X aria-hidden="true" /></button>
            </div>
          </header>
          <div className="public-event-dialog-body">
            {detailLoading ? <div className="public-event-detail-loading" aria-live="polite">Đang tải chi tiết...</div> : null}
            {detailError ? <div className="alert error" role="alert">{detailError}</div> : null}
            <EventDetails event={selectedEvent} />
          </div>
          <footer className="public-event-dialog-footer"><button className="btn ghost" type="button" onClick={closeDetail}>Đóng</button></footer>
        </article>
      </dialog> : null}
    </>
  )
}

function EventLoadingState() {
  return <div className="event-directory-list" aria-label="Đang tải sự kiện" aria-busy="true">{[1, 2, 3].map((item) => <div className="event-directory-skeleton" key={item}><span /><div><b /><i /></div><em /></div>)}</div>
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
      <span className="event-directory-meta"><span><CalendarDays aria-hidden="true" />{formatEventDateTime(event.startAt)}</span>{hasMeaningfulEnd ? <span><Clock3 aria-hidden="true" />Kết thúc {formatEventDateTime(event.endAt)}</span> : null}</span>
      <span className="event-directory-meta event-directory-meta-secondary"><span>{event.mode === 'ONLINE' ? <Video aria-hidden="true" /> : <MapPin aria-hidden="true" />}{EVENT_MODE_LABELS[event.mode]}{location ? ` · ${location}` : ''}</span><span>{EVENT_VISIBILITY_LABELS[event.visibility]}</span></span>
      {event.content?.trim() ? <span className="event-directory-preview">{event.content.trim()}</span> : null}
    </span>
    <span className="event-directory-side"><StatusBadge event={event} /><span className="event-directory-detail">Xem chi tiết <ArrowRight size={16} aria-hidden="true" /></span></span>
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
    <section><h3>Thời gian</h3><dl className="public-event-detail-grid"><div><dt><CalendarDays aria-hidden="true" />Bắt đầu</dt><dd>{formatEventDateTime(event.startAt)}</dd></div>{hasMeaningfulEnd ? <div><dt><Clock3 aria-hidden="true" />Kết thúc</dt><dd>{formatEventDateTime(event.endAt)}</dd></div> : null}</dl></section>
    <section><h3>Hình thức</h3><dl className="public-event-detail-grid"><div><dt>{event.mode === 'ONLINE' ? <Video aria-hidden="true" /> : <MapPin aria-hidden="true" />}Hình thức</dt><dd>{EVENT_MODE_LABELS[event.mode]}</dd></div>{location ? <div><dt><MapPin aria-hidden="true" />Địa điểm</dt><dd>{location}</dd></div> : null}<div><dt>Phạm vi</dt><dd>{EVENT_VISIBILITY_LABELS[event.visibility]}</dd></div></dl>{event.mode === 'ONLINE' && event.meetingUrl?.trim() ? <a className="btn brand sm public-event-meeting-link" href={event.meetingUrl} target="_blank" rel="noreferrer"><Video aria-hidden="true" /> Tham gia trực tuyến <ExternalLink size={15} aria-hidden="true" /></a> : null}</section>
    {event.content?.trim() ? <section><h3>Nội dung</h3><p className="public-event-detail-content">{event.content.trim()}</p></section> : null}
    {creatorName ? <section><h3>Thông tin</h3><dl className="public-event-detail-grid"><div><dt><UserRound aria-hidden="true" />Người tạo</dt><dd>{creatorName}</dd></div></dl></section> : null}
  </div>
}


function asTimeFilter(value: string | null): TimeFilter {
  return value === 'UPCOMING' || value === 'PAST' ? value : 'ALL'
}

function asEventStatus(value: string | null): EventStatus | 'ALL' {
  return value && EVENT_STATUSES.includes(value as EventStatus) ? value as EventStatus : 'ALL'
}

function parsePage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
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
