import {
  Archive,
  ArrowUp,
  Calendar,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Download,
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  Filter,
  Presentation,
  RotateCcw,
  Search,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiClientError } from '../../../lib/apiClient'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { Pagination } from '../../../shared/components/Pagination'
import { listPublicProjects } from '../../projects/api'
import type { PublicProjectSummary } from '../../projects/types'
import { PublicPageHead } from '../components/PublicPageHead'
import {
  listPublicDocumentCategories,
  listPublicDocumentYears,
  listPublicDocuments,
  publicDocumentFileUrl,
} from '../documentApi'
import {
  PUBLIC_DOCUMENT_FILE_TYPES,
  PUBLIC_DOCUMENT_SORTS,
  type PublicDocumentCategory,
  type PublicDocumentFileType,
  type PublicDocumentSort,
  type PublicDocumentSummary,
} from '../documentTypes'
import './PublicDocumentsPage.css'

const PAGE_SIZE = 12
const MIN_YEAR = 2000

const FILE_TYPE_LABELS: Record<PublicDocumentFileType, string> = {
  ALL: 'Tất cả loại tệp',
  PDF: 'PDF',
  DOCUMENT: 'Văn bản (Word, Docs)',
  SPREADSHEET: 'Bảng tính (Excel, CSV)',
  PRESENTATION: 'Trình chiếu (PowerPoint)',
  IMAGE: 'Hình ảnh',
  ARCHIVE: 'Tệp nén (ZIP, RAR)',
  OTHER: 'Định dạng khác',
}

const SORT_LABELS: Record<PublicDocumentSort, string> = {
  LATEST: 'Mới cập nhật',
  OLDEST: 'Cũ nhất',
  TITLE_ASC: 'Tên A - Z',
  TITLE_DESC: 'Tên Z - A',
}

export function PublicDocumentsPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const query = searchParams.get('q') ?? ''
  const categoryParam = searchParams.get('category')
  const categoryFilter = categoryParam ? categoryParam.trim() : 'ALL'
  const projectId = parseProjectId(searchParams.get('project'))
  const fileType = parseFileType(searchParams.get('type'))
  const yearFilter = searchParams.get('year') ?? 'ALL'
  const year = parseYear(searchParams.get('year'))
  const sort = parseSort(searchParams.get('sort'))
  const page = parsePage(searchParams.get('page'))

  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [availableYears, setAvailableYears] = useState<number[]>([])
  const [categories, setCategories] = useState<PublicDocumentCategory[]>([])
  const [items, setItems] = useState<PublicDocumentSummary[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  // Collapsible controls for sidebar matching /du-an
  const [isYearExpanded, setIsYearExpanded] = useState(false)
  const [isFilterOpen, setIsFilterOpen] = useState(true)
  const [showScrollTop, setShowScrollTop] = useState(false)

  // Category tabs horizontal scroll management
  const tabsRef = useRef<HTMLDivElement | null>(null)
  const [canScrollLeft, setCanScrollLeft] = useState(false)
  const [canScrollRight, setCanScrollRight] = useState(false)

  // Fetch projects and available years
  useEffect(() => {
    let active = true
    void listPublicProjects(0, 48)
      .then((res) => {
        if (active) setProjects(res.items)
      })
      .catch(() => {
        // Fallback
      })

    void listPublicDocumentYears()
      .then((res) => {
        if (active && Array.isArray(res)) setAvailableYears(res)
      })
      .catch(() => {
        // Fallback
      })

    return () => {
      active = false
    }
  }, [])

  // Build list of 10 recent years
  const displayYears = useMemo(() => {
    const currentYear = new Date().getFullYear()
    const default10 = Array.from({ length: 10 }, (_, i) => currentYear - i)
    const set = new Set([...availableYears, ...default10])
    return Array.from(set).sort((a, b) => b - a)
  }, [availableYears])

  // Compact 3-year options before expansion
  const collapsedYearOptions = useMemo<{ key: number | 'ALL'; label: string }[]>(() => {
    if (displayYears.length === 0) {
      return [{ key: 'ALL', label: 'Tất cả' }]
    }

    const newestYear = displayYears[0]

    if (yearFilter === 'ALL' || yearFilter === String(newestYear)) {
      return [
        { key: 'ALL', label: 'Tất cả' },
        { key: newestYear, label: String(newestYear) },
        ...(displayYears.length > 1 ? [{ key: displayYears[1], label: String(displayYears[1]) }] : []),
      ]
    }

    const selectedYearNum = Number(yearFilter)
    const idx = displayYears.indexOf(selectedYearNum)

    if (idx !== -1) {
      const leftKey = idx === 0 ? 'ALL' : displayYears[idx - 1]
      const leftLabel = leftKey === 'ALL' ? 'Tất cả' : String(leftKey)
      const rightKey = idx < displayYears.length - 1 ? displayYears[idx + 1] : selectedYearNum - 1
      return [
        { key: leftKey, label: leftLabel },
        { key: selectedYearNum, label: String(selectedYearNum) },
        { key: rightKey, label: String(rightKey) },
      ]
    }

    return [
      { key: 'ALL', label: 'Tất cả' },
      { key: newestYear, label: String(newestYear) },
      ...(displayYears.length > 1 ? [{ key: displayYears[1], label: String(displayYears[1]) }] : []),
    ]
  }, [displayYears, yearFilter])

  // Scroll listener for "Cuộn lên đầu trang" button
  useEffect(() => {
    const handleScroll = () => {
      setShowScrollTop(window.scrollY > 200)
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    handleScroll()
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  // Calculate and sync tab scroll boundary states
  const updateTabsScrollState = useCallback(() => {
    const el = tabsRef.current
    if (!el) return
    const { scrollLeft, scrollWidth, clientWidth } = el
    setCanScrollLeft(scrollLeft > 4)
    setCanScrollRight(scrollLeft + clientWidth < scrollWidth - 4)
  }, [])

  // Auto-scroll active tab into view and sync scroll indicator state
  useEffect(() => {
    const el = tabsRef.current
    if (!el) return
    const activeTab = el.querySelector<HTMLElement>('.project-folder-tab.is-active')
    if (activeTab) {
      activeTab.scrollIntoView({ inline: 'nearest', behavior: 'smooth', block: 'nearest' })
    }
    const timer = setTimeout(updateTabsScrollState, 50)
    return () => clearTimeout(timer)
  }, [categoryFilter, categories, updateTabsScrollState])

  // Mouse wheel and resize listener for horizontal tab navigation
  useEffect(() => {
    const el = tabsRef.current
    if (!el) return

    const handleScroll = () => {
      updateTabsScrollState()
    }

    const handleWheel = (e: WheelEvent) => {
      // Allow trackpad native horizontal swipe without interception
      if (Math.abs(e.deltaX) > Math.abs(e.deltaY)) return
      if (e.deltaY === 0) return
      // When at edges, let page scroll vertically normally
      if (
        (e.deltaY < 0 && el.scrollLeft <= 0) ||
        (e.deltaY > 0 && el.scrollLeft + el.clientWidth >= el.scrollWidth - 1)
      ) {
        return
      }
      e.preventDefault()
      el.scrollLeft += e.deltaY
      updateTabsScrollState()
    }

    el.addEventListener('scroll', handleScroll, { passive: true })
    el.addEventListener('wheel', handleWheel, { passive: false })
    window.addEventListener('resize', updateTabsScrollState)
    updateTabsScrollState()

    return () => {
      el.removeEventListener('scroll', handleScroll)
      el.removeEventListener('wheel', handleWheel)
      window.removeEventListener('resize', updateTabsScrollState)
    }
  }, [updateTabsScrollState])

  // Scroll tabs horizontally via chevron controls
  const scrollTabs = (direction: 'left' | 'right') => {
    const el = tabsRef.current
    if (!el) return
    const step = Math.max(el.clientWidth * 0.6, 220)
    el.scrollBy({ left: direction === 'left' ? -step : step, behavior: 'smooth' })
  }

  // Load categories whenever year changes
  useEffect(() => {
    const controller = new AbortController()
    void listPublicDocumentCategories(year ?? undefined, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setCategories(data)
      })
      .catch(() => {
        // Fallback
      })
    return () => controller.abort()
  }, [year])

  // Fetch document items
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    const effectiveCategory =
      categoryFilter && categoryFilter.toUpperCase() !== 'ALL'
        ? categoryFilter.trim()
        : undefined

    void listPublicDocuments(
      page - 1,
      PAGE_SIZE,
      {
        query: query.trim() || undefined,
        projectId: projectId ?? undefined,
        category: effectiveCategory,
        fileType,
        year: year ?? undefined,
        sort,
      },
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) return
        setItems(response.items)
        setTotalElements(response.totalElements)
        setTotalPages(response.totalPages)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setItems([])
          setTotalElements(0)
          setTotalPages(0)
          setError(publicDocumentError(reason))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [categoryFilter, fileType, page, projectId, query, reloadKey, sort, year])

  function updateFilter(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value === 'ALL' || value === '') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearchParams(next)
  }

  function handleCategoryTabClick(code: string) {
    const next = new URLSearchParams(searchParams)
    if (code === 'ALL' || code.toLowerCase() === categoryFilter.toLowerCase()) {
      next.delete('category')
    } else {
      next.set('category', code)
    }
    next.delete('page')
    setSearchParams(next)
  }

  function handleYearClick(y: number | 'ALL') {
    const next = new URLSearchParams(searchParams)
    if (y === 'ALL' || String(y) === yearFilter) {
      next.delete('year')
    } else {
      next.set('year', String(y))
    }
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
    const target = document.getElementById('docs-main-content')
    if (target) {
      target.scrollIntoView({ behavior: 'smooth' })
    }
  }

  const totalYearDocCount = useMemo(
    () => categories.reduce((sum, c) => sum + (c.documentCount || 0), 0),
    [categories],
  )

  const activeCategoryLabel = useMemo(() => {
    if (categoryFilter === 'ALL') return 'Tất cả'
    const found = categories.find((c) => c.code.toLowerCase() === categoryFilter.toLowerCase())
    return found ? found.name : categoryFilter
  }, [categories, categoryFilter])

  const activeYearLabel = yearFilter === 'ALL' ? 'Tất cả' : yearFilter

  return (
    <>
      <PublicPageHead
        title="Tài liệu"
        description="Tra cứu và tải về các tài liệu nghiên cứu khoa học, đặc tả kỹ thuật, hướng dẫn quy trình và báo cáo chuyên môn của phòng thí nghiệm Smart Lab."
      />
      <div className="project-archive-page public-docs-page">
        <div className="wrap project-archive-container">
          {/* Top Page Header Synchronized with /du-an */}
          <header className="project-archive-header">
            <div className="project-archive-header-left">
              <h1 className="project-archive-title">Kho Tài liệu Nghiên cứu &amp; Chuyên môn</h1>
              <p className="project-archive-subtitle">
                Tra cứu, tham khảo và tải về các tài liệu nghiên cứu khoa học, đặc tả kỹ thuật, hướng dẫn quy trình và báo cáo chuyên môn của phòng thí nghiệm Smart Lab.
              </p>
            </div>
            <div className="project-archive-header-right">
              <div className="project-archive-status-badge">
                <span className="project-archive-status-dot" aria-hidden="true" />
                <span>Lưu trữ mở</span>
              </div>
            </div>
          </header>

          {/* 2-Column Layout: Left Sidebar & Right Content */}
          <div className="project-archive-layout">
            {/* Left Sidebar: Filters */}
            <aside className="project-archive-sidebar" aria-label="Bộ lọc tài liệu">
              <div className="project-sidebar-card">
                {/* Sidebar Header */}
                <div className="project-sidebar-top">
                  <div className="project-sidebar-heading">
                    <Filter size={16} className="project-sidebar-icon" aria-hidden="true" />
                    <span>BỘ LỌC TÌM KIẾM</span>
                  </div>
                  <button
                    className="project-sidebar-reset-btn"
                    type="button"
                    onClick={resetFilters}
                    title="Đặt lại toàn bộ bộ lọc"
                  >
                    <RotateCcw size={13} aria-hidden="true" />
                    <span>Đặt lại</span>
                  </button>
                </div>

                {/* Keyword Search */}
                <div className="project-sidebar-group">
                  <label className="project-sidebar-label" htmlFor="doc-search-input">
                    TỪ KHÓA TÌM KIẾM
                  </label>
                  <div className="project-sidebar-search-box">
                    <Search size={15} className="project-sidebar-search-icon" aria-hidden="true" />
                    <input
                      id="doc-search-input"
                      className="project-sidebar-search-input"
                      type="search"
                      value={query}
                      onChange={(event) => updateFilter('q', event.target.value)}
                      placeholder="Tên tài liệu, tệp tin, mô tả..."
                    />
                  </div>
                </div>

                {/* Year Filter: Compact 3-Year / Expandable */}
                <div className="project-sidebar-group">
                  <div className="project-sidebar-group-title">
                    <Calendar size={15} className="project-sidebar-group-icon" aria-hidden="true" />
                    <span>NĂM PHÁT HÀNH ({displayYears.length} NĂM)</span>
                  </div>

                  {isYearExpanded ? (
                    <div className="project-years-expanded-box">
                      {/* All Years Button */}
                      <button
                        type="button"
                        className={`project-year-all-btn ${yearFilter === 'ALL' ? 'is-active' : ''}`}
                        onClick={() => handleYearClick('ALL')}
                      >
                        Tất cả các năm
                      </button>

                      {/* Scrollable grid if > 9 years */}
                      <div className={`project-years-grid ${displayYears.length > 9 ? 'is-scrollable' : ''}`}>
                        {displayYears.map((y) => {
                          const isSelected = yearFilter === String(y)
                          return (
                            <button
                              key={y}
                              type="button"
                              className={`project-year-btn ${isSelected ? 'is-active' : ''}`}
                              onClick={() => handleYearClick(y)}
                            >
                              {y}
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  ) : (
                    /* Compact 3-button row: equal width */
                    <div className="project-years-compact-row">
                      {collapsedYearOptions.map((opt) => {
                        const isSelected = opt.key === 'ALL' ? yearFilter === 'ALL' : yearFilter === String(opt.key)
                        return (
                          <button
                            key={opt.key}
                            type="button"
                            className={`project-year-btn ${isSelected ? 'is-active' : ''}`}
                            onClick={() => handleYearClick(opt.key)}
                          >
                            {opt.label}
                          </button>
                        )
                      })}
                    </div>
                  )}

                  {/* Expand / Collapse Button */}
                  <button
                    type="button"
                    className="project-year-toggle-btn"
                    onClick={() => setIsYearExpanded((prev) => !prev)}
                  >
                    <span>{isYearExpanded ? 'Thu gọn năm' : 'Mở rộng năm'}</span>
                    {isYearExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                  </button>
                </div>

                {/* Categories & File Types Accordion */}
                <div className="project-sidebar-group project-sidebar-accordion">
                  <button
                    type="button"
                    className="project-accordion-header"
                    onClick={() => setIsFilterOpen((prev) => !prev)}
                    aria-expanded={isFilterOpen}
                  >
                    <span className="project-accordion-title">
                      <span className="project-sidebar-group-icon">❖</span>
                      <span>Chuyên mục &amp; Loại tệp</span>
                    </span>
                    {isFilterOpen ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
                  </button>

                  {isFilterOpen && (
                    <div className="project-accordion-body">
                      {/* Document Category Dropdown */}
                      <div className="project-sidebar-subgroup">
                        <label className="project-sidebar-sublabel" htmlFor="doc-category-select">
                          CHUYÊN MỤC TÀI LIỆU
                        </label>
                        <select
                          id="doc-category-select"
                          className="project-sidebar-select"
                          value={categoryFilter}
                          onChange={(e) => updateFilter('category', e.target.value)}
                        >
                          <option value="ALL">Tất cả chuyên mục</option>
                          {categories.map((c) => (
                            <option key={c.code} value={c.code}>
                              {c.name} ({c.documentCount})
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* File Type Dropdown */}
                      <div className="project-sidebar-subgroup">
                        <label className="project-sidebar-sublabel" htmlFor="doc-filetype-select">
                          LOẠI HÌNH TỆP TIN
                        </label>
                        <select
                          id="doc-filetype-select"
                          className="project-sidebar-select"
                          value={fileType}
                          onChange={(e) => updateFilter('type', e.target.value)}
                        >
                          {PUBLIC_DOCUMENT_FILE_TYPES.map((t) => (
                            <option key={t} value={t}>
                              {FILE_TYPE_LABELS[t]}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Project Association Dropdown */}
                      <div className="project-sidebar-subgroup">
                        <label className="project-sidebar-sublabel" htmlFor="doc-project-select">
                          DỰ ÁN LIÊN KẾT
                        </label>
                        <select
                          id="doc-project-select"
                          className="project-sidebar-select"
                          value={projectId ? String(projectId) : 'ALL'}
                          onChange={(e) => updateFilter('project', e.target.value)}
                        >
                          <option value="ALL">Tất cả dự án</option>
                          {projects.map((p) => (
                            <option key={p.id} value={String(p.id)}>
                              {p.name}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Sort Dropdown */}
                      <div className="project-sidebar-subgroup">
                        <label className="project-sidebar-sublabel" htmlFor="doc-sort-select">
                          THỨ TỰ SẮP XẾP
                        </label>
                        <select
                          id="doc-sort-select"
                          className="project-sidebar-select"
                          value={sort}
                          onChange={(e) => updateFilter('sort', e.target.value)}
                        >
                          {PUBLIC_DOCUMENT_SORTS.map((s) => (
                            <option key={s} value={s}>
                              {SORT_LABELS[s]}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                  )}
                </div>

                {/* Sidebar Summary Footer */}
                <div className="project-sidebar-footer-box">
                  <span className="project-sidebar-footer-year">
                    Năm: <strong>{activeYearLabel}</strong>
                  </span>
                  <span className="project-sidebar-footer-count">
                    {totalElements} tài liệu phù hợp
                  </span>
                </div>
              </div>
            </aside>

            {/* Right Main Content */}
            <main className="project-archive-main" id="docs-main-content">
              {/* Folder Tabs (Category Tabs) */}
              <div
                className={`project-folder-tabs-wrapper ${canScrollLeft ? 'has-scroll-left' : ''} ${canScrollRight ? 'has-scroll-right' : ''}`}
              >
                {canScrollLeft && (
                  <button
                    type="button"
                    className="project-folder-tabs-scroll-btn is-prev"
                    onClick={() => scrollTabs('left')}
                    aria-label="Cuộn các chuyên mục sang trái"
                    title="Cuộn sang trái"
                  >
                    <ChevronLeft size={16} />
                  </button>
                )}
                <div
                  ref={tabsRef}
                  className="project-folder-tabs"
                  role="tablist"
                  aria-label="Lọc theo chuyên mục"
                >
                  <button
                    type="button"
                    role="tab"
                    aria-selected={categoryFilter === 'ALL'}
                    className={`project-folder-tab ${categoryFilter === 'ALL' ? 'is-active' : ''}`}
                    onClick={() => handleCategoryTabClick('ALL')}
                  >
                    <span className="project-folder-tab-label">Tất cả</span>
                    <span className="project-folder-tab-count">{totalYearDocCount}</span>
                  </button>
                  {categories.map((cat) => {
                    const isActive = categoryFilter.toLowerCase() === cat.code.toLowerCase()
                    return (
                      <button
                        key={cat.id}
                        type="button"
                        role="tab"
                        aria-selected={isActive}
                        className={`project-folder-tab ${isActive ? 'is-active' : ''}`}
                        onClick={() => handleCategoryTabClick(cat.code)}
                      >
                        <span className="project-folder-tab-label">{cat.name}</span>
                        <span className="project-folder-tab-count">{cat.documentCount}</span>
                      </button>
                    )
                  })}
                </div>
                {canScrollRight && (
                  <button
                    type="button"
                    className="project-folder-tabs-scroll-btn is-next"
                    onClick={() => scrollTabs('right')}
                    aria-label="Cuộn các chuyên mục sang phải"
                    title="Cuộn sang phải"
                  >
                    <ChevronRight size={16} />
                  </button>
                )}
              </div>

              {/* Sub-toolbar / Result stats bar */}
              <div className="project-archive-meta-bar">
                <div className="project-meta-left">
                  <span>Chuyên mục: <strong>{activeCategoryLabel}</strong></span>
                  <span className="project-meta-sep">•</span>
                  <span>Năm: <strong>{activeYearLabel}</strong></span>
                  {fileType !== 'ALL' && (
                    <>
                      <span className="project-meta-sep">•</span>
                      <span>Định dạng: <strong>{FILE_TYPE_LABELS[fileType]}</strong></span>
                    </>
                  )}
                </div>
                <div className="project-meta-right">
                  Hiển thị {items.length} / {totalElements} tài liệu (Click thẻ để xem hoặc tải tệp)
                </div>
              </div>

              {/* Error Message */}
              {error ? (
                <div className="project-archive-error" role="alert">
                  <Feedback error={error} />
                  <button className="btn" type="button" onClick={() => setReloadKey((v) => v + 1)}>
                    Thử tải lại
                  </button>
                </div>
              ) : null}

              {/* Loading */}
              {loading && items.length === 0 ? (
                <div className="public-empty empty tight project-loading-box" aria-busy="true">
                  Đang tải danh sách tài liệu...
                </div>
              ) : null}

              {/* Empty State */}
              {!loading && !error && items.length === 0 ? (
                <EmptyState
                  title="Không tìm thấy tài liệu phù hợp"
                  description="Thử thay đổi năm phát hành, từ khóa hoặc điều kiện lọc ở cột bên trái."
                />
              ) : null}

              {/* Cards Grid */}
              {items.length > 0 ? (
                <>
                  <div className="project-archive-grid" aria-busy={loading}>
                    {items.map((document) => (
                      <PublicDocumentCard document={document} key={document.id} />
                    ))}
                  </div>

                  {/* Server-side Pagination */}
                  {totalPages > 1 ? (
                    <div className="public-docs-pagination-container">
                      <Pagination page={page} totalPages={totalPages} onChange={updatePage} />
                    </div>
                  ) : null}
                </>
              ) : null}
            </main>
          </div>
        </div>

        {/* Scroll to top floating button */}
        <button
          type="button"
          className={`project-scroll-top-btn ${showScrollTop ? 'is-visible' : ''}`}
          onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
          title="Cuộn lên đầu trang"
          aria-label="Cuộn lên đầu trang"
          {...{ alt: 'Cuộn lên đầu trang' }}
        >
          <ArrowUp size={20} aria-hidden="true" />
          <span className="sr-only">Cuộn lên đầu trang</span>
        </button>
      </div>
    </>
  )
}

function PublicDocumentCard({ document }: { document: PublicDocumentSummary }) {
  const { icon: Icon, typeClass, formatLabel } = resolveFileVisual(
    document.mimeType,
    document.originalFileName,
  )

  const downloadUrl = publicDocumentFileUrl(document.currentFileId)

  return (
    <div className="project-card doc-card">
      <div className="project-card-inner doc-card-inner">
        {/* Top Badges Row */}
        <div className="project-card-top-row doc-card-top-row">
          <div className="project-card-top-left">
            <span className={`doc-filetype-badge ${typeClass}`}>
              <Icon size={12} aria-hidden="true" />
              <span>{formatLabel}</span>
            </span>
          </div>

          <div className="project-card-top-right">
            {document.categoryName && (
              <span className="doc-category-badge">
                {document.categoryName}
              </span>
            )}
          </div>
        </div>

        {/* Document Title */}
        <h3 className="project-card-title doc-card-title">
          <a
            href={downloadUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="doc-title-link"
            title={document.title}
          >
            {document.title}
          </a>
        </h3>

        {/* Document Description */}
        <p className="project-card-desc doc-card-desc">
          {document.description || 'Tài liệu lưu trữ chính thức của phòng thí nghiệm Smart Lab.'}
        </p>

        {/* Project & Version Chips */}
        <div className="project-card-chips doc-card-chips">
          {document.projectName && (
            <span className="project-tech-chip doc-project-chip" title={document.projectName}>
              {document.projectCode ? `${document.projectCode} • ` : ''}{document.projectName}
            </span>
          )}
          <span className="project-tech-chip doc-version-chip">
            v{document.currentVersionNo || 1}
          </span>
        </div>

        {/* Card Footer: Metadata & Download CTA */}
        <div className="project-card-footer doc-card-footer">
          <div className="doc-card-meta-left">
            <span className="doc-card-size">{formatBytes(document.sizeBytes)}</span>
            <span className="doc-card-meta-sep">•</span>
            <span className="doc-card-date">
              <Calendar size={12} aria-hidden="true" />
              <span>{formatDate(document.updatedAt)}</span>
            </span>
          </div>

          <div className="doc-card-actions-right">
            <a
              href={downloadUrl}
              download={document.originalFileName}
              className="doc-download-btn"
              aria-label={`Tải xuống tài liệu ${document.title}`}
              title="Tải xuống tệp tin"
            >
              <Download size={13} aria-hidden="true" />
              <span>Tải tệp</span>
            </a>
          </div>
        </div>
      </div>

      {/* Hover Overlay at Card Bottom: Matching Project Card */}
      <a
        href={downloadUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="project-card-hover-overlay doc-card-hover-overlay"
        aria-hidden="true"
        tabIndex={-1}
      >
        <span className="project-card-hover-action">Tải tài liệu ngay →</span>
        <span className="project-card-hover-hint">Nhấp để mở / tải</span>
      </a>
    </div>
  )
}

function parseProjectId(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function parseYear(value: string | null) {
  if (!value || !/^\d{4}$/.test(value)) return null
  const parsed = Number(value)
  return parsed >= MIN_YEAR && parsed <= new Date().getFullYear() ? parsed : null
}

function parseFileType(value: string | null): PublicDocumentFileType {
  return value && PUBLIC_DOCUMENT_FILE_TYPES.includes(value as PublicDocumentFileType)
    ? (value as PublicDocumentFileType)
    : 'ALL'
}

function parseSort(value: string | null): PublicDocumentSort {
  return value && PUBLIC_DOCUMENT_SORTS.includes(value as PublicDocumentSort)
    ? (value as PublicDocumentSort)
    : 'LATEST'
}

function parsePage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 1
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 1
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(date)
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value < 0) return 'Không rõ kích thước'
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB']
  let size = value / 1024
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ${units[index]}`
}

function resolveFileVisual(mimeType: string, name: string) {
  const mime = mimeType.toLowerCase()
  const lowerName = name.toLowerCase()
  if (mime === 'application/pdf' || lowerName.endsWith('.pdf')) {
    return { icon: FileText, typeClass: 'type-pdf', formatLabel: 'PDF' }
  }
  if (mime.startsWith('image/')) {
    return { icon: FileImage, typeClass: 'type-img', formatLabel: 'IMG' }
  }
  if (
    mime.includes('spreadsheet') ||
    mime.includes('excel') ||
    mime === 'text/csv' ||
    /\.(xlsx|xls|csv)$/.test(lowerName)
  ) {
    return { icon: FileSpreadsheet, typeClass: 'type-sheet', formatLabel: 'XLSX' }
  }
  if (
    mime.includes('presentation') ||
    mime.includes('powerpoint') ||
    /\.(pptx|ppt)$/.test(lowerName)
  ) {
    return { icon: Presentation, typeClass: 'type-pres', formatLabel: 'PPTX' }
  }
  if (
    mime.includes('zip') ||
    mime.includes('rar') ||
    mime.includes('7z') ||
    /\.(zip|rar|7z)$/.test(lowerName)
  ) {
    return { icon: Archive, typeClass: 'type-archive', formatLabel: 'ZIP' }
  }
  if (
    mime.startsWith('text/') ||
    mime.includes('word') ||
    mime.includes('document') ||
    /\.(docx|doc)$/.test(lowerName)
  ) {
    return { icon: FileText, typeClass: 'type-doc', formatLabel: 'DOCX' }
  }
  return { icon: File, typeClass: 'type-other', formatLabel: 'FILE' }
}

function publicDocumentError(reason: unknown) {
  return reason instanceof ApiClientError && reason.status >= 500
    ? 'Không thể tải tài liệu lúc này. Vui lòng thử lại.'
    : 'Không thể tải tài liệu. Vui lòng thử lại.'
}
