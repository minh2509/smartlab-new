import { ChevronLeft, ChevronRight, RotateCcw, Search, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiClientError } from '../../../lib/apiClient'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { listPublicProjects } from '../../projects/api'
import type { PublicProjectSummary } from '../../projects/types'
import { PublicPageHead } from '../components/PublicPageHead'
import { listPublicGallery, listPublicGalleryYears, publicGalleryFileUrl } from '../galleryApi'
import {
  GALLERY_CATEGORIES,
  GALLERY_SORTS,
  type GalleryCategory,
  type GallerySort,
  type PublicGalleryItem,
} from '../galleryTypes'
import fallbackImage from '../../../assets/fields/ai-research.webp'
import './PublicGalleryPage.css'

const PAGE_SIZE = 24
const MIN_YEAR = 2000

const CATEGORY_LABELS: Record<GalleryCategory, string> = {
  ALL: 'Tất cả danh mục',
  WORKSHOP: 'Workshop',
  PROJECT_DEMO: 'Demo dự án',
  EVENT: 'Sự kiện',
  LAB_ACTIVITY: 'Hoạt động Lab',
  OTHER: 'Khác',
}

const SORT_LABELS: Record<GallerySort, string> = {
  LATEST: 'Mới nhất',
  OLDEST: 'Cũ nhất',
}

export function PublicGalleryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const category = parseCategory(searchParams.get('category'))
  const projectId = parsePositive(searchParams.get('project'))
  const year = parseYear(searchParams.get('year'))
  const sort = parseSort(searchParams.get('sort'))
  const page = parsePage(searchParams.get('page'))

  const [items, setItems] = useState<PublicGalleryItem[]>([])
  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [years, setYears] = useState<number[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [optionsError, setOptionsError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)
  const tileRefs = useRef<Array<HTMLButtonElement | null>>([])
  const dialogRef = useRef<HTMLDivElement>(null)
  const lastFocused = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setOptionsError(false)
    void Promise.all([
      listPublicProjects(0, 48, {}, controller.signal),
      listPublicGalleryYears(controller.signal),
    ])
      .then(([projectPage, availableYears]) => {
        if (!controller.signal.aborted) {
          setProjects(projectPage.items)
          setYears(availableYears)
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setOptionsError(true)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void listPublicGallery(
      {
        query: query.trim() || undefined,
        category: category === 'ALL' ? undefined : category,
        projectId: projectId ?? undefined,
        year: year ?? undefined,
        sort,
        page: page - 1,
        size: PAGE_SIZE,
      },
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) return
        setItems(response.items)
        setTotal(response.totalElements)
        setTotalPages(response.totalPages)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setItems([])
          setTotal(0)
          setTotalPages(0)
          setError(
            reason instanceof ApiClientError && reason.status >= 500
              ? 'Không thể tải thư viện ảnh lúc này. Vui lòng thử lại.'
              : 'Không thể tải thư viện ảnh. Vui lòng thử lại.',
          )
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [category, page, projectId, query, reloadKey, sort, year])

  useEffect(() => {
    if (lightboxIndex === null) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialogRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeLightbox()
      if (event.key === 'ArrowLeft') {
        setLightboxIndex((value) => (value === null ? null : (value - 1 + items.length) % items.length))
      }
      if (event.key === 'ArrowRight') {
        setLightboxIndex((value) => (value === null ? null : (value + 1) % items.length))
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKey)
    }
  }, [items.length, lightboxIndex])

  function openLightbox(index: number) {
    lastFocused.current = document.activeElement as HTMLElement
    setLightboxIndex(index)
  }

  function closeLightbox() {
    setLightboxIndex(null)
    requestAnimationFrame(() => lastFocused.current?.focus())
  }

  function updateFilter(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value === 'ALL' || value === '') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearchParams(next)
  }

  function resetFilters() {
    setSearchParams(new URLSearchParams())
  }

  function updatePage(newPage: number) {
    const next = new URLSearchParams(searchParams)
    if (newPage <= 1) next.delete('page')
    else next.set('page', String(newPage))
    setSearchParams(next)
  }

  const hasFilters = Boolean(query.trim()) || category !== 'ALL' || projectId !== null || year !== null || sort !== 'LATEST'

  const categoryOptions = useMemo(
    () => [
      { value: 'ALL', label: 'Tất cả danh mục' },
      ...GALLERY_CATEGORIES.filter((c) => c !== 'ALL').map((c) => ({
        value: c,
        label: CATEGORY_LABELS[c],
      })),
    ],
    [],
  )

  const projectOptions = useMemo(
    () => [
      { value: 'ALL', label: optionsError ? 'Không tải được dự án' : 'Tất cả dự án' },
      ...projects.map((p) => ({ value: String(p.id), label: p.name })),
    ],
    [optionsError, projects],
  )

  const yearOptions = useMemo(
    () => [
      { value: 'ALL', label: optionsError ? 'Không tải được năm' : 'Tất cả năm' },
      ...years.map((y) => ({ value: String(y), label: String(y) })),
    ],
    [optionsError, years],
  )

  const sortOptions = useMemo(
    () => [
      { value: 'LATEST', label: SORT_LABELS.LATEST },
      { value: 'OLDEST', label: SORT_LABELS.OLDEST },
    ],
    [],
  )

  return (
    <>
      <PublicPageHead
        title="Thư viện ảnh"
        description="Những hình ảnh được Smart Lab công khai từ hoạt động, workshop và các dự án."
      />

      <section className="section public-gallery-section">
        <div className="wrap">
          <div className="public-gallery-intro">
            <div>
              <div className="kicker">THƯ VIỆN ẢNH</div>
              <h2>Thư viện ảnh</h2>
              <p>Những khoảnh khắc và sản phẩm hình ảnh được Smart Lab chia sẻ công khai.</p>
            </div>
            <span className="public-archive-page-size">24 ảnh / trang</span>
          </div>

          <div className="gallery-archive-toolbar" role="search">
            <label className="gallery-archive-search">
              <span className="sr-only">Tìm kiếm ảnh</span>
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => updateFilter('q', event.target.value)}
                placeholder="Tìm ảnh theo tên hoặc nội dung..."
              />
            </label>

            <PopupSelect
              value={category}
              options={categoryOptions}
              onChange={(value) => updateFilter('category', value)}
              ariaLabel="Lọc theo danh mục ảnh"
              className="gallery-archive-filter"
            />

            <PopupSelect
              value={projectId ? String(projectId) : 'ALL'}
              options={projectOptions}
              onChange={(value) => updateFilter('project', value)}
              ariaLabel="Lọc theo dự án"
              className="gallery-archive-filter"
              disabled={optionsError}
            />

            <PopupSelect
              value={year ? String(year) : 'ALL'}
              options={yearOptions}
              onChange={(value) => updateFilter('year', value)}
              ariaLabel="Lọc theo năm"
              className="gallery-archive-filter"
              disabled={optionsError}
            />

            <PopupSelect
              value={sort}
              options={sortOptions}
              onChange={(value) => updateFilter('sort', value)}
              ariaLabel="Sắp xếp ảnh"
              className="gallery-archive-filter"
            />

            {hasFilters ? (
              <button className="gallery-archive-reset" type="button" onClick={resetFilters}>
                <RotateCcw size={15} aria-hidden="true" />
                Đặt lại
              </button>
            ) : null}
          </div>

          {error ? (
            <div className="gallery-archive-request-state" role="alert">
              <Feedback error={error} />
              <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            </div>
          ) : null}

          {loading && items.length === 0 ? (
            <div className="public-empty empty tight gallery-archive-request-state" aria-busy="true">
              Đang tải thư viện ảnh...
            </div>
          ) : null}

          {!loading && !error && items.length === 0 ? (
            <EmptyState
              title={hasFilters ? 'Không tìm thấy ảnh phù hợp' : 'Chưa có ảnh công khai'}
              description={
                hasFilters
                  ? 'Thử thay đổi từ khóa hoặc bộ lọc.'
                  : 'Những hình ảnh công khai của Smart Lab sẽ xuất hiện tại đây.'
              }
            />
          ) : null}

          {items.length > 0 ? (
            <>
              <div className="gallery-archive-results-bar">
                <p className="gallery-archive-summary" aria-live="polite">
                  {formatRange(page, PAGE_SIZE, total)} · {total} ảnh
                </p>
                {loading ? (
                  <span className="gallery-archive-refreshing" aria-live="polite">
                    Đang cập nhật...
                  </span>
                ) : null}
              </div>
              <div className="public-gallery-grid" aria-busy={loading}>
                {items.map((item, index) => (
                  <GalleryTile
                    item={item}
                    key={item.id}
                    buttonRef={(node) => {
                      tileRefs.current[index] = node
                    }}
                    onOpen={() => openLightbox(index)}
                  />
                ))}
              </div>
              <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
            </>
          ) : null}
        </div>
      </section>

      {lightboxIndex !== null && items[lightboxIndex] ? (
        <GalleryLightbox
          item={items[lightboxIndex]}
          index={lightboxIndex}
          count={items.length}
          dialogRef={dialogRef}
          onClose={closeLightbox}
          onPrevious={() =>
            setLightboxIndex((value) => (value === null ? null : (value - 1 + items.length) % items.length))
          }
          onNext={() =>
            setLightboxIndex((value) => (value === null ? null : (value + 1) % items.length))
          }
        />
      ) : null}
    </>
  )
}

function GalleryTile({
  item,
  onOpen,
  buttonRef,
}: {
  item: PublicGalleryItem
  onOpen: () => void
  buttonRef: (node: HTMLButtonElement | null) => void
}) {
  return (
    <article className="public-gallery-tile">
      <button ref={buttonRef} type="button" onClick={onOpen} aria-label={`Mở ảnh ${item.title}`}>
        <img
          src={publicGalleryFileUrl(item.fileId)}
          alt={item.altText || item.title}
          onError={(event) => {
            event.currentTarget.src = fallbackImage
          }}
        />
        <span className="public-gallery-tile-overlay">
          <strong>{item.title}</strong>
          <small>{item.caption || item.projectName || 'Smart Lab'}</small>
        </span>
      </button>
      <div className="public-gallery-tile-meta">
        <span>{CATEGORY_LABELS[item.category]}</span>
        <time dateTime={item.capturedAt || item.publishedAt}>
          {formatDate(item.capturedAt || item.publishedAt)}
        </time>
      </div>
    </article>
  )
}

function GalleryLightbox({
  item,
  index,
  count,
  dialogRef,
  onClose,
  onPrevious,
  onNext,
}: {
  item: PublicGalleryItem
  index: number
  count: number
  dialogRef: RefObject<HTMLDivElement | null>
  onClose: () => void
  onPrevious: () => void
  onNext: () => void
}) {
  return (
    <div
      className="public-gallery-lightbox-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className="public-gallery-lightbox"
        role="dialog"
        aria-modal="true"
        aria-labelledby="gallery-lightbox-title"
        tabIndex={-1}
        ref={dialogRef}
      >
        <button className="public-gallery-lightbox-close" type="button" onClick={onClose} aria-label="Đóng ảnh">
          <X aria-hidden="true" />
        </button>
        <div className="public-gallery-lightbox-media">
          <img
            src={publicGalleryFileUrl(item.fileId)}
            alt={item.altText || item.title}
            onError={(event) => {
              event.currentTarget.src = fallbackImage
            }}
          />
        </div>
        <div className="public-gallery-lightbox-content">
          <span className="kicker">{CATEGORY_LABELS[item.category]}</span>
          <h2 id="gallery-lightbox-title">{item.title}</h2>
          {item.caption ? <p>{item.caption}</p> : null}
          <div className="public-gallery-lightbox-meta">
            <time dateTime={item.capturedAt || item.publishedAt}>
              {formatDate(item.capturedAt || item.publishedAt)}
            </time>
            {item.projectName ? <span>{item.projectName}</span> : null}
            {item.eventTitle ? <span>{item.eventTitle}</span> : null}
          </div>
        </div>
        <div className="public-gallery-lightbox-nav">
          <button type="button" onClick={onPrevious} disabled={count < 2} aria-label="Ảnh trước">
            <ChevronLeft aria-hidden="true" />
          </button>
          <span>
            {index + 1} / {count}
          </span>
          <button type="button" onClick={onNext} disabled={count < 2} aria-label="Ảnh tiếp theo">
            <ChevronRight aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  )
}

function parseCategory(value: string | null): GalleryCategory {
  return value && GALLERY_CATEGORIES.includes(value as GalleryCategory) ? (value as GalleryCategory) : 'ALL'
}

function parseSort(value: string | null): GallerySort {
  return value && GALLERY_SORTS.includes(value as GallerySort) ? (value as GallerySort) : 'LATEST'
}

function parsePositive(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function parseYear(value: string | null) {
  if (!value || !/^\d{4}$/.test(value)) return null
  const parsed = Number(value)
  return parsed >= MIN_YEAR && parsed <= new Date().getFullYear() ? parsed : null
}

function parsePage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 1
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 1
}

function formatRange(page: number, size: number, total: number) {
  return total === 0 ? '0 / 0' : `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
}
