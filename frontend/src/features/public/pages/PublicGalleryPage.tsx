import {
  ArrowRight,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Download,
  Layers,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Tag,
  X,
} from 'lucide-react'
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
  ALL: 'Tất cả',
  WORKSHOP: 'Workshop',
  PROJECT_DEMO: 'Demo dự án',
  EVENT: 'Sự kiện',
  LAB_ACTIVITY: 'Đời sống Lab',
  OTHER: 'Khác',
}

const CATEGORY_TABS: { key: GalleryCategory; label: string }[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'WORKSHOP', label: 'Workshop' },
  { key: 'PROJECT_DEMO', label: 'Demo dự án' },
  { key: 'EVENT', label: 'Sự kiện' },
  { key: 'LAB_ACTIVITY', label: 'Đời sống Lab' },
  { key: 'OTHER', label: 'Khác' },
]

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
  const [filterDrawerOpen, setFilterDrawerOpen] = useState(false)

  const tileRefs = useRef<Array<HTMLButtonElement | null>>([])
  const dialogRef = useRef<HTMLDivElement>(null)
  const drawerRef = useRef<HTMLDivElement>(null)
  const lastFocused = useRef<HTMLElement | null>(null)

  // Fetch projects and available years
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

  // Fetch gallery items
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

  // Lightbox keyboard controls and scroll lock
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

  // Filter drawer keyboard controls
  useEffect(() => {
    if (!filterDrawerOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setFilterDrawerOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [filterDrawerOpen])

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

  function handleCategoryTabClick(catKey: GalleryCategory) {
    const next = new URLSearchParams(searchParams)
    if (catKey === 'ALL') {
      next.delete('category')
    } else {
      next.set('category', catKey)
    }
    next.delete('page')
    setSearchParams(next)
  }

  function removeFilterChip(key: 'q' | 'category' | 'project' | 'year' | 'sort') {
    const next = new URLSearchParams(searchParams)
    next.delete(key)
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

  const selectedProject = useMemo(() => {
    if (!projectId) return null
    return projects.find((p) => p.id === projectId)
  }, [projectId, projects])

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

  const hasFilters =
    Boolean(query.trim()) ||
    category !== 'ALL' ||
    projectId !== null ||
    year !== null ||
    sort !== 'LATEST'

  const secondaryFilterCount =
    (query.trim() ? 1 : 0) +
    (projectId !== null ? 1 : 0) +
    (year !== null ? 1 : 0) +
    (sort !== 'LATEST' ? 1 : 0)

  // Identify Hero Spotlight item (on page 1 when no heavy filtering is active)
  const isPageOneWithoutSearch = page === 1 && !query.trim()
  const heroIndex = useMemo(() => {
    if (!isPageOneWithoutSearch || items.length === 0) return -1
    const featuredIdx = items.findIndex((item) => item.isFeatured)
    return featuredIdx >= 0 ? featuredIdx : 0
  }, [isPageOneWithoutSearch, items])

  const heroItem = heroIndex >= 0 ? items[heroIndex] : null

  return (
    <>
      <PublicPageHead
        title="Thư viện ảnh"
        description="Những hình ảnh được Smart Lab công khai từ hoạt động nghiên cứu, workshop và các dự án thực tế."
      />

      <div className="gallery-chronicle-root">
        <div className="wrap">
          {/* Editorial Exhibition Header */}
          <header className="gallery-curated-header">
            <div className="gallery-header-narrative">
              <span className="gallery-editorial-kicker">Ký ức &amp; Tư liệu</span>
              <h1 className="gallery-editorial-heading">Thư viện Hình ảnh &amp; Đời sống Lab</h1>
              <p className="gallery-editorial-subtext">
                Khám phá những khoảnh khắc chế tạo, thử nghiệm công nghệ, các buổi hội thảo khoa học và nhịp sống học thuật tại phòng thí nghiệm Smart Lab.
              </p>
            </div>

            {/* Curated Navigation Bar: Level 1 Category Pills + Contextual Filter Drawer Button */}
            <div className="gallery-curated-nav-row">
              <nav className="gallery-curated-category-tabs" role="tablist" aria-label="Danh mục tư liệu">
                {CATEGORY_TABS.map((tab) => {
                  const isActive = category === tab.key
                  return (
                    <button
                      key={tab.key}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      className={`gallery-editorial-tab ${isActive ? 'is-active' : ''}`}
                      onClick={() => handleCategoryTabClick(tab.key)}
                    >
                      {tab.label}
                    </button>
                  )
                })}
              </nav>

              <div className="gallery-curated-action-cluster">
                <button
                  type="button"
                  className={`gallery-editorial-filter-btn ${secondaryFilterCount > 0 ? 'is-active' : ''}`}
                  onClick={() => setFilterDrawerOpen(true)}
                  aria-expanded={filterDrawerOpen}
                  aria-controls="gallery-contextual-drawer"
                  aria-label="Mở bộ lọc nâng cao"
                >
                  <SlidersHorizontal size={15} aria-hidden="true" />
                  <span>Bộ lọc</span>
                  {secondaryFilterCount > 0 ? (
                    <span className="gallery-editorial-filter-count">{secondaryFilterCount}</span>
                  ) : null}
                </button>
              </div>
            </div>

            {/* Active Filter Context Banner (Clean, Minimalist) */}
            {hasFilters ? (
              <div className="gallery-active-filter-strip" aria-label="Bộ lọc đang chọn">
                <span className="gallery-active-filter-label">Đang lọc:</span>
                <div className="gallery-active-filter-pills">
                  {category !== 'ALL' ? (
                    <span className="gallery-filter-pill">
                      <span>{CATEGORY_LABELS[category]}</span>
                      <button
                        type="button"
                        onClick={() => removeFilterChip('category')}
                        aria-label={`Bỏ lọc ${CATEGORY_LABELS[category]}`}
                      >
                        <X size={12} aria-hidden="true" />
                      </button>
                    </span>
                  ) : null}

                  {query.trim() ? (
                    <span className="gallery-filter-pill">
                      <span>"{query.trim()}"</span>
                      <button
                        type="button"
                        onClick={() => removeFilterChip('q')}
                        aria-label={`Bỏ lọc từ khóa ${query}`}
                      >
                        <X size={12} aria-hidden="true" />
                      </button>
                    </span>
                  ) : null}

                  {selectedProject ? (
                    <span className="gallery-filter-pill">
                      <span>{selectedProject.name}</span>
                      <button
                        type="button"
                        onClick={() => removeFilterChip('project')}
                        aria-label={`Bỏ lọc dự án ${selectedProject.name}`}
                      >
                        <X size={12} aria-hidden="true" />
                      </button>
                    </span>
                  ) : null}

                  {year !== null ? (
                    <span className="gallery-filter-pill">
                      <span>Năm {year}</span>
                      <button
                        type="button"
                        onClick={() => removeFilterChip('year')}
                        aria-label={`Bỏ lọc năm ${year}`}
                      >
                        <X size={12} aria-hidden="true" />
                      </button>
                    </span>
                  ) : null}

                  {sort !== 'LATEST' ? (
                    <span className="gallery-filter-pill">
                      <span>{SORT_LABELS[sort]}</span>
                      <button
                        type="button"
                        onClick={() => removeFilterChip('sort')}
                        aria-label="Bỏ sắp xếp"
                      >
                        <X size={12} aria-hidden="true" />
                      </button>
                    </span>
                  ) : null}

                  <button
                    type="button"
                    className="gallery-filter-reset-action"
                    onClick={resetFilters}
                  >
                    Xóa tất cả
                  </button>
                </div>
              </div>
            ) : null}
          </header>

          {/* Error Request State */}
          {error ? (
            <div className="gallery-error-panel" role="alert">
              <Feedback error={error} />
              <button
                className="btn btn-primary"
                type="button"
                onClick={() => setReloadKey((value) => value + 1)}
              >
                Thử tải lại
              </button>
            </div>
          ) : null}

          {/* Skeleton Loading State */}
          {loading && items.length === 0 ? (
            <div className="gallery-editorial-skeleton-flow" aria-busy="true">
              <div className="skeleton-spotlight-frame shimmer" />
              <div className="skeleton-mosaic-flow">
                {Array.from({ length: 6 }, (_, i) => (
                  <div key={i} className={`skeleton-mosaic-tile shimmer tile-${i % 4}`} />
                ))}
              </div>
            </div>
          ) : null}

          {/* Empty State */}
          {!loading && !error && items.length === 0 ? (
            <div className="gallery-empty-state-card">
              <EmptyState
                title={hasFilters ? 'Không tìm thấy hình ảnh phù hợp' : 'Chưa có hình ảnh công khai'}
                description={
                  hasFilters
                    ? 'Thử thay đổi từ khóa, danh mục hoặc đặt lại bộ lọc để khám phá các khoảnh khắc khác.'
                    : 'Các hình ảnh ghi lại hoạt động nghiên cứu của Smart Lab sẽ sớm xuất hiện tại đây.'
                }
              />
              {hasFilters ? (
                <button
                  type="button"
                  className="gallery-empty-reset-btn"
                  onClick={resetFilters}
                >
                  <RotateCcw size={15} aria-hidden="true" />
                  <span>Đặt lại bộ lọc</span>
                </button>
              ) : null}
            </div>
          ) : null}

          {/* Main Visual Stream */}
          {items.length > 0 ? (
            <div className="gallery-content-stream" aria-busy={loading}>
              {/* Results status indicator */}
              <div className="gallery-stream-meta-line">
                <span className="gallery-stream-count">
                  {formatRange(page, PAGE_SIZE, total)} · <strong>{total}</strong> ảnh tư liệu
                </span>
                <span className="gallery-stream-page-tag">24 ảnh / trang</span>
              </div>

              {/* 1. Hero Spotlight Showcase (Displayed when hero item exists) */}
              {heroItem && (
                <section className="gallery-hero-spotlight" aria-label="Khoảnh khắc tiêu biểu">
                  <button
                    ref={(node) => {
                      tileRefs.current[heroIndex] = node
                    }}
                    type="button"
                    className="gallery-spotlight-trigger"
                    onClick={() => openLightbox(heroIndex)}
                    aria-label={`Mở xem tiêu điểm: ${heroItem.title}`}
                  >
                    <div className="gallery-spotlight-viewport">
                      <img
                        src={publicGalleryFileUrl(heroItem.fileId)}
                        alt={heroItem.altText || heroItem.title}
                        loading="eager"
                        decoding="async"
                        onError={(event) => {
                          event.currentTarget.src = fallbackImage
                        }}
                      />
                      <div className="gallery-spotlight-overlay" />
                      <div className="gallery-spotlight-narrative">
                        <div className="gallery-spotlight-pill-row">
                          <span className="gallery-spotlight-category-chip">
                            {CATEGORY_LABELS[heroItem.category]}
                          </span>
                          <span className="gallery-spotlight-badge">Tiêu điểm</span>
                        </div>
                        <h2 className="gallery-spotlight-title">{heroItem.title}</h2>
                        {heroItem.caption ? (
                          <p className="gallery-spotlight-caption">{heroItem.caption}</p>
                        ) : null}
                        <div className="gallery-spotlight-footer">
                          <time dateTime={heroItem.capturedAt || heroItem.publishedAt}>
                            {formatDate(heroItem.capturedAt || heroItem.publishedAt)}
                          </time>
                          {heroItem.projectName ? (
                            <span>· Dự án: {heroItem.projectName}</span>
                          ) : null}
                          <span className="gallery-spotlight-cta">
                            <span>Xem ảnh chi tiết</span>
                            <ArrowRight size={14} aria-hidden="true" />
                          </span>
                        </div>
                      </div>
                    </div>
                  </button>
                </section>
              )}

              {/* 2. Editorial Mosaic Stream (All supporting items) */}
              <section className="gallery-editorial-mosaic" aria-label="Tất cả tư liệu hình ảnh">
                {items.map((item, index) => {
                  // If hero is shown in spotlight, skip repeating it as the first mosaic item
                  if (heroItem && index === heroIndex) return null
                  const isFeatured = item.isFeatured
                  // Rhythmic variation based on index
                  const rhythmClass = isFeatured
                    ? 'is-wide-feature'
                    : index % 7 === 1
                      ? 'is-portrait-tall'
                      : index % 5 === 0
                        ? 'is-landscape-wide'
                        : 'is-standard-frame'

                  return (
                    <article key={item.id} className={`gallery-mosaic-card ${rhythmClass}`}>
                      <button
                        ref={(node) => {
                          tileRefs.current[index] = node
                        }}
                        type="button"
                        className="gallery-mosaic-btn"
                        onClick={() => openLightbox(index)}
                        aria-label={`Mở xem ảnh: ${item.title}`}
                      >
                        <div className="gallery-mosaic-media-box">
                          <img
                            src={publicGalleryFileUrl(item.fileId)}
                            alt={item.altText || item.title}
                            loading="lazy"
                            decoding="async"
                            onError={(event) => {
                              event.currentTarget.src = fallbackImage
                            }}
                          />
                          <div className="gallery-mosaic-hover-scrim">
                            <span className="gallery-mosaic-hover-cat">
                              {CATEGORY_LABELS[item.category]}
                            </span>
                            <span className="gallery-mosaic-hover-action">Xem toàn màn hình</span>
                          </div>
                        </div>

                        <div className="gallery-mosaic-info">
                          <h3 className="gallery-mosaic-title">{item.title}</h3>
                          <div className="gallery-mosaic-submeta">
                            <time dateTime={item.capturedAt || item.publishedAt}>
                              {formatDate(item.capturedAt || item.publishedAt)}
                            </time>
                            {item.projectName ? (
                              <span className="gallery-mosaic-project" title={item.projectName}>
                                {item.projectName}
                              </span>
                            ) : item.eventTitle ? (
                              <span className="gallery-mosaic-event" title={item.eventTitle}>
                                {item.eventTitle}
                              </span>
                            ) : null}
                          </div>
                        </div>
                      </button>
                    </article>
                  )
                })}
              </section>

              {/* Server-side Pagination */}
              <div className="gallery-editorial-pagination">
                <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
              </div>
            </div>
          ) : null}
        </div>
      </div>

      {/* Contextual Filter Slide-over Drawer */}
      {filterDrawerOpen ? (
        <div
          className="gallery-drawer-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setFilterDrawerOpen(false)
          }}
        >
          <div
            id="gallery-contextual-drawer"
            className="gallery-contextual-drawer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="gallery-drawer-heading"
            ref={drawerRef}
          >
            <div className="gallery-drawer-header">
              <div className="gallery-drawer-title-group">
                <SlidersHorizontal size={17} aria-hidden="true" />
                <h2 id="gallery-drawer-heading" className="gallery-drawer-title">
                  Bộ lọc thư viện ảnh
                </h2>
              </div>
              <button
                type="button"
                className="gallery-drawer-close-btn"
                onClick={() => setFilterDrawerOpen(false)}
                aria-label="Đóng bảng bộ lọc"
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <div className="gallery-drawer-body">
              {/* Search keyword */}
              <div className="gallery-drawer-field">
                <label className="gallery-drawer-label" htmlFor="drawer-search-input">
                  Từ khóa tìm kiếm
                </label>
                <div className="gallery-drawer-search-box">
                  <Search size={15} className="gallery-drawer-search-icon" aria-hidden="true" />
                  <input
                    id="drawer-search-input"
                    className="gallery-drawer-search-input"
                    type="search"
                    value={query}
                    onChange={(event) => updateFilter('q', event.target.value)}
                    placeholder="Tìm theo tên ảnh, sự kiện, dự án..."
                  />
                </div>
              </div>

              {/* Project selector */}
              <div className="gallery-drawer-field">
                <label className="gallery-drawer-label">Dự án liên kết</label>
                <PopupSelect
                  value={projectId ? String(projectId) : 'ALL'}
                  options={projectOptions}
                  onChange={(value) => updateFilter('project', value)}
                  ariaLabel="Lọc theo dự án"
                  className="gallery-drawer-select"
                  disabled={optionsError}
                />
              </div>

              {/* Year selector */}
              <div className="gallery-drawer-field">
                <label className="gallery-drawer-label">Năm phát hành</label>
                <PopupSelect
                  value={year ? String(year) : 'ALL'}
                  options={yearOptions}
                  onChange={(value) => updateFilter('year', value)}
                  ariaLabel="Lọc theo năm"
                  className="gallery-drawer-select"
                  disabled={optionsError}
                />
              </div>

              {/* Sort selector */}
              <div className="gallery-drawer-field">
                <label className="gallery-drawer-label">Sắp xếp hiển thị</label>
                <PopupSelect
                  value={sort}
                  options={sortOptions}
                  onChange={(value) => updateFilter('sort', value)}
                  ariaLabel="Sắp xếp thời gian"
                  className="gallery-drawer-select"
                />
              </div>
            </div>

            <div className="gallery-drawer-footer">
              {hasFilters ? (
                <button
                  type="button"
                  className="gallery-drawer-reset-btn"
                  onClick={() => {
                    resetFilters()
                    setFilterDrawerOpen(false)
                  }}
                >
                  <RotateCcw size={14} aria-hidden="true" />
                  <span>Xóa bộ lọc</span>
                </button>
              ) : null}
              <button
                type="button"
                className="gallery-drawer-apply-btn"
                onClick={() => setFilterDrawerOpen(false)}
              >
                Xem kết quả ({total} ảnh)
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Photography Exhibition Lightbox Modal */}
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
      className="gallery-lightbox-exhibition-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className="gallery-lightbox-exhibition-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="gallery-lightbox-title"
        tabIndex={-1}
        ref={dialogRef}
      >
        <button
          className="gallery-lightbox-close-trigger"
          type="button"
          onClick={onClose}
          aria-label="Đóng ảnh"
        >
          <X size={20} aria-hidden="true" />
        </button>

        {/* Media Frame (Clean, Large, Exhibition Quality) */}
        <div className="gallery-lightbox-media-viewport">
          <img
            src={publicGalleryFileUrl(item.fileId)}
            alt={item.altText || item.title}
            onError={(event) => {
              event.currentTarget.src = fallbackImage
            }}
          />
        </div>

        {/* Story & Context Sidebar (Pure Editorial, No Technical Slop) */}
        <div className="gallery-lightbox-story-sidebar">
          <div className="gallery-lightbox-meta-pills">
            <span className="gallery-lightbox-category-chip">
              {CATEGORY_LABELS[item.category]}
            </span>
            {item.isFeatured ? (
              <span className="gallery-lightbox-featured-chip">Tiêu điểm</span>
            ) : null}
          </div>

          <h2 id="gallery-lightbox-title" className="gallery-lightbox-headline">
            {item.title}
          </h2>

          {item.caption ? (
            <p className="gallery-lightbox-story-text">{item.caption}</p>
          ) : null}

          {/* Context Highlights */}
          <div className="gallery-lightbox-context-card">
            <div className="gallery-lightbox-context-row">
              <span className="context-label">
                <Calendar size={14} aria-hidden="true" />
                <span>Thời gian:</span>
              </span>
              <time className="context-value" dateTime={item.capturedAt || item.publishedAt}>
                {formatDate(item.capturedAt || item.publishedAt)}
              </time>
            </div>

            {item.projectName ? (
              <div className="gallery-lightbox-context-row">
                <span className="context-label">
                  <Layers size={14} aria-hidden="true" />
                  <span>Dự án:</span>
                </span>
                <span className="context-value">{item.projectName}</span>
              </div>
            ) : null}

            {item.eventTitle ? (
              <div className="gallery-lightbox-context-row">
                <span className="context-label">
                  <Tag size={14} aria-hidden="true" />
                  <span>Sự kiện:</span>
                </span>
                <span className="context-value">{item.eventTitle}</span>
              </div>
            ) : null}
          </div>

          {/* Action Download High-Res File */}
          <div className="gallery-lightbox-action-row">
            <a
              href={publicGalleryFileUrl(item.fileId)}
              target="_blank"
              rel="noopener noreferrer"
              download={item.originalFileName || true}
              className="gallery-lightbox-download-action"
            >
              <Download size={15} aria-hidden="true" />
              <span>Tải ảnh gốc</span>
            </a>
          </div>
        </div>

        {/* Bottom Navigation Frame */}
        <div className="gallery-lightbox-nav-strip">
          <button
            type="button"
            className="gallery-lightbox-nav-arrow"
            onClick={onPrevious}
            disabled={count < 2}
            aria-label="Xem ảnh trước"
          >
            <ChevronLeft size={18} aria-hidden="true" />
            <span>Trước</span>
          </button>

          <div className="gallery-lightbox-nav-counter">
            <span>
              <strong>{index + 1}</strong> / {count}
            </span>
          </div>

          <button
            type="button"
            className="gallery-lightbox-nav-arrow"
            onClick={onNext}
            disabled={count < 2}
            aria-label="Xem ảnh tiếp theo"
          >
            <span>Tiếp theo</span>
            <ChevronRight size={18} aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  )
}

function parseCategory(value: string | null): GalleryCategory {
  return value && GALLERY_CATEGORIES.includes(value as GalleryCategory)
    ? (value as GalleryCategory)
    : 'ALL'
}

function parseSort(value: string | null): GallerySort {
  return value && GALLERY_SORTS.includes(value as GallerySort)
    ? (value as GallerySort)
    : 'LATEST'
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
  return total === 0 ? '0 / 0' : `${(page - 1) * size + 1} - ${Math.min(page * size, total)} / ${total}`
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
}
