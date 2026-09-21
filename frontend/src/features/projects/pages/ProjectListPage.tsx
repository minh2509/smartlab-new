import {
  Calendar,
  ChevronDown,
  ChevronUp,
  Filter,
  Loader2,
  RotateCcw,
  Search,
  UsersRound,
} from 'lucide-react'
import { useEffect, useMemo, useState, useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import type { ResearchField } from '../../../shared/types/api'
import { ApiClientError } from '../../../lib/apiClient'
import { getResearchFields } from '../../profile/api'
import { listPublicProjects, listPublicProjectYears } from '../api'
import {
  PROJECT_TYPE_LABELS,
  PROJECT_TYPES,
  PUBLIC_PROJECT_STATUS_LABELS,
  PUBLIC_PROJECT_STATUSES,
} from '../types'
import type { PublicProjectSummary, ProjectType, PublicProjectStatus } from '../types'
import './ProjectListPage.css'

const INITIAL_PAGE_SIZE = 6
const LOAD_MORE_BATCH_SIZE = 6

type FilterValue = 'ALL' | string

type StatusTabKey = 'ALL' | PublicProjectStatus

interface StatusTabConfig {
  key: StatusTabKey
  label: string
}

const STATUS_TABS: StatusTabConfig[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'RECRUITING', label: 'Đang tuyển' },
  { key: 'UPCOMING', label: 'Sắp triển khai' },
  { key: 'ACTIVE', label: 'Đang thực hiện' },
  { key: 'COMPLETED', label: 'Đã hoàn thành' },
]

export function ProjectListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [researchFields, setResearchFields] = useState<ResearchField[]>([])
  const [fieldsError, setFieldsError] = useState<string | null>(null)
  const [availableYears, setAvailableYears] = useState<number[]>([])
  const [reloadKey, setReloadKey] = useState(0)

  // Status counts for folder tabs
  const [tabCounts, setTabCounts] = useState<Record<StatusTabKey, number | null>>({
    ALL: null,
    RECRUITING: null,
    UPCOMING: null,
    ACTIVE: null,
    COMPLETED: null,
  })

  // Collapsible controls for sidebar
  const [isYearExpanded, setIsYearExpanded] = useState(false)
  const [isFieldFilterOpen, setIsFieldFilterOpen] = useState(true)

  const query = searchParams.get('q') ?? ''
  const typeFilter = asProjectType(searchParams.get('projectType'))
  // Default to RECRUITING if not specified in searchParams
  const statusFilterRaw = searchParams.get('status')
  const statusFilter: StatusTabKey = statusFilterRaw
    ? (statusFilterRaw === 'ALL' ? 'ALL' : asPublicProjectStatus(statusFilterRaw))
    : 'RECRUITING'
  const fieldFilter = searchParams.get('field') ?? 'ALL'
  const yearFilter = searchParams.get('year') ?? 'ALL'

  // Fetch research fields and available years
  useEffect(() => {
    let active = true
    void getResearchFields()
      .then((result) => {
        if (active) setResearchFields(result)
      })
      .catch((reason: unknown) => {
        if (active) setFieldsError(reason instanceof Error ? reason.message : 'Không tải được bộ lọc lĩnh vực.')
      })

    void listPublicProjectYears()
      .then((years) => {
        if (active && Array.isArray(years)) setAvailableYears(years)
      })
      .catch(() => {
        // Fallback
      })

    return () => {
      active = false
    }
  }, [])

  // Build list of 10 recent years (from current year down to currentYear - 9)
  const displayYears = useMemo(() => {
    const currentYear = new Date().getFullYear()
    const default10 = Array.from({ length: 10 }, (_, i) => currentYear - i)
    const set = new Set([...availableYears, ...default10])
    return Array.from(set).sort((a, b) => b - a)
  }, [availableYears])

  // Compact 3-year options before expansion:
  // - When 'ALL' or newest year: [ 'ALL' ("Tất cả"), newestYear, newestYear - 1 ]
  // - When a specific year is selected: [ year + 1 (or 'ALL'), year (middle), year - 1 ]
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

  // Initial fetch when filters change (resets project list to first 6 items)
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    const selectedYearNum = yearFilter !== 'ALL' && !Number.isNaN(Number(yearFilter)) ? Number(yearFilter) : undefined

    void listPublicProjects(0, INITIAL_PAGE_SIZE, {
      query,
      researchFieldCode: fieldFilter === 'ALL' ? undefined : fieldFilter,
      projectType: typeFilter === 'ALL' ? undefined : typeFilter,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      year: selectedYearNum,
    }, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setProjects(result.items)
        setTotalElements(result.totalElements)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setProjects([])
          setTotalElements(0)
          setError(projectListError(reason))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [fieldFilter, query, reloadKey, statusFilter, typeFilter, yearFilter])

  // Fetch tab counts in background
  useEffect(() => {
    const controller = new AbortController()
    const selectedYearNum = yearFilter !== 'ALL' && !Number.isNaN(Number(yearFilter)) ? Number(yearFilter) : undefined
    const baseFilters = {
      query,
      researchFieldCode: fieldFilter === 'ALL' ? undefined : fieldFilter,
      projectType: typeFilter === 'ALL' ? undefined : typeFilter,
      year: selectedYearNum,
    }

    Promise.allSettled([
      listPublicProjects(0, 1, { ...baseFilters }, controller.signal),
      listPublicProjects(0, 1, { ...baseFilters, status: 'RECRUITING' }, controller.signal),
      listPublicProjects(0, 1, { ...baseFilters, status: 'UPCOMING' }, controller.signal),
      listPublicProjects(0, 1, { ...baseFilters, status: 'ACTIVE' }, controller.signal),
      listPublicProjects(0, 1, { ...baseFilters, status: 'COMPLETED' }, controller.signal),
    ]).then((results) => {
      if (controller.signal.aborted) return
      setTabCounts({
        ALL: results[0].status === 'fulfilled' ? results[0].value.totalElements : null,
        RECRUITING: results[1].status === 'fulfilled' ? results[1].value.totalElements : null,
        UPCOMING: results[2].status === 'fulfilled' ? results[2].value.totalElements : null,
        ACTIVE: results[3].status === 'fulfilled' ? results[3].value.totalElements : null,
        COMPLETED: results[4].status === 'fulfilled' ? results[4].value.totalElements : null,
      })
    })

    return () => controller.abort()
  }, [fieldFilter, query, reloadKey, typeFilter, yearFilter])

  // Incremental Load More: load 2 more projects and append to list
  const handleLoadMore = useCallback(() => {
    if (loadingMore || projects.length >= totalElements) return

    setLoadingMore(true)
    const currentLength = projects.length
    const nextPage = Math.floor(currentLength / LOAD_MORE_BATCH_SIZE)
    const selectedYearNum = yearFilter !== 'ALL' && !Number.isNaN(Number(yearFilter)) ? Number(yearFilter) : undefined

    void listPublicProjects(nextPage, LOAD_MORE_BATCH_SIZE, {
      query,
      researchFieldCode: fieldFilter === 'ALL' ? undefined : fieldFilter,
      projectType: typeFilter === 'ALL' ? undefined : typeFilter,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      year: selectedYearNum,
    })
      .then((result) => {
        // Prevent duplicate IDs when appending
        setProjects((prev) => {
          const existingIds = new Set(prev.map((p) => p.id))
          const fresh = result.items.filter((item) => !existingIds.has(item.id))
          return [...prev, ...fresh]
        })
        setTotalElements(result.totalElements)
      })
      .catch((reason: unknown) => {
        setError(projectListError(reason))
      })
      .finally(() => {
        setLoadingMore(false)
      })
  }, [fieldFilter, loadingMore, projects.length, query, statusFilter, totalElements, typeFilter, yearFilter])

  function updateFilter(key: string, value: FilterValue) {
    const next = new URLSearchParams(searchParams)
    if (value === 'ALL' || value === '') next.delete(key)
    else next.set(key, value)
    setSearchParams(next)
  }

  function handleStatusTabClick(tabKey: StatusTabKey) {
    const next = new URLSearchParams(searchParams)
    if (tabKey === 'ALL') {
      next.set('status', 'ALL')
    } else {
      next.set('status', tabKey)
    }
    setSearchParams(next)
  }

  function handleYearClick(year: number | 'ALL') {
    const next = new URLSearchParams(searchParams)
    if (year === 'ALL' || String(year) === yearFilter) {
      next.delete('year')
    } else {
      next.set('year', String(year))
    }
    setSearchParams(next)
  }

  function resetFilters() {
    setSearchParams(new URLSearchParams())
  }

  const activeStatusLabel = statusFilter === 'ALL' ? 'Tất cả' : PUBLIC_PROJECT_STATUS_LABELS[statusFilter]
  const activeYearLabel = yearFilter === 'ALL' ? 'Tất cả' : yearFilter
  const remainingCount = Math.max(0, totalElements - projects.length)

  return (
    <div className="project-archive-page">
      <div className="wrap project-archive-container">
        {/* Top Page Header (Without "Đang mở đăng ký đề tài...") */}
        <header className="project-archive-header">
          <div className="project-archive-header-left">
            <h1 className="project-archive-title">Kho Dự án Nghiên cứu &amp; Ứng dụng</h1>
            <p className="project-archive-subtitle">
              Tra cứu và đăng ký tham gia các dự án nghiên cứu khoa học, đề tài công nghệ thực chiến của phòng thí nghiệm Smart Lab.
            </p>
          </div>
        </header>

        {/* 2-Column Layout: Left Sidebar & Right Content */}
        <div className="project-archive-layout">
          {/* Left Sidebar: Filters */}
          <aside className="project-archive-sidebar" aria-label="Bộ lọc dự án">
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
                <label className="project-sidebar-label" htmlFor="project-search-input">
                  TỪ KHÓA TÌM KIẾM
                </label>
                <div className="project-sidebar-search-box">
                  <Search size={15} className="project-sidebar-search-icon" aria-hidden="true" />
                  <input
                    id="project-search-input"
                    className="project-sidebar-search-input"
                    type="search"
                    value={query}
                    onChange={(event) => updateFilter('q', event.target.value)}
                    placeholder="Mã PRJ, tên, công nghệ..."
                  />
                </div>
              </div>

              {/* Year Filter: Compact 3-Year / Expandable */}
              <div className="project-sidebar-group">
                <div className="project-sidebar-group-title">
                  <Calendar size={15} className="project-sidebar-group-icon" aria-hidden="true" />
                  <span>NĂM THỰC HIỆN ({displayYears.length} NĂM)</span>
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

              {/* Field & Type Accordion */}
              <div className="project-sidebar-group project-sidebar-accordion">
                <button
                  type="button"
                  className="project-accordion-header"
                  onClick={() => setIsFieldFilterOpen((prev) => !prev)}
                  aria-expanded={isFieldFilterOpen}
                >
                  <span className="project-accordion-title">
                    <span className="project-sidebar-group-icon">❖</span>
                    <span>Lĩnh vực &amp; Loại dự án</span>
                  </span>
                  {isFieldFilterOpen ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
                </button>

                {isFieldFilterOpen && (
                  <div className="project-accordion-body">
                    {/* Research Field Dropdown */}
                    <div className="project-sidebar-subgroup">
                      <label className="project-sidebar-sublabel" htmlFor="project-field-select">
                        LĨNH VỰC CHUYÊN MÔN
                      </label>
                      <select
                        id="project-field-select"
                        className="project-sidebar-select"
                        value={fieldFilter}
                        onChange={(e) => updateFilter('field', e.target.value)}
                        disabled={Boolean(fieldsError)}
                      >
                        <option value="ALL">Tất cả lĩnh vực</option>
                        {researchFields.map((f) => (
                          <option key={f.code} value={f.code}>
                            {f.name}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Project Type Dropdown */}
                    <div className="project-sidebar-subgroup">
                      <label className="project-sidebar-sublabel" htmlFor="project-type-select">
                        LOẠI HÌNH ĐỀ TÀI
                      </label>
                      <select
                        id="project-type-select"
                        className="project-sidebar-select"
                        value={typeFilter}
                        onChange={(e) => updateFilter('projectType', e.target.value)}
                      >
                        <option value="ALL">Tất cả</option>
                        {PROJECT_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {PROJECT_TYPE_LABELS[t]}
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
                  {totalElements} dự án phù hợp
                </span>
              </div>
            </div>
          </aside>

          {/* Right Main Content */}
          <main className="project-archive-main" id="project-list-content">
            {/* Folder Tabs */}
            <div className="project-folder-tabs" role="tablist" aria-label="Lọc theo trạng thái dự án">
              {STATUS_TABS.map((tab) => {
                const isActive = statusFilter === tab.key
                const count = tabCounts[tab.key]
                return (
                  <button
                    key={tab.key}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    className={`project-folder-tab ${isActive ? 'is-active' : ''}`}
                    onClick={() => handleStatusTabClick(tab.key)}
                  >
                    <span className="project-folder-tab-label">{tab.label}</span>
                    {count !== null && (
                      <span className="project-folder-tab-count">{count}</span>
                    )}
                  </button>
                )
              })}
            </div>

            {/* Sub-toolbar / Result stats bar */}
            <div className="project-archive-meta-bar">
              <div className="project-meta-left">
                <span>Trạng thái: <strong>{activeStatusLabel}</strong></span>
                <span className="project-meta-sep">•</span>
                <span>Năm: <strong>{activeYearLabel}</strong></span>
              </div>
              <div className="project-meta-right">
                Hiển thị {projects.length} / {totalElements} dự án (Click thẻ để xem chi tiết)
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
            {loading && projects.length === 0 ? (
              <div className="public-empty empty tight project-loading-box" aria-busy="true">
                Đang tải danh sách đề tài...
              </div>
            ) : null}

            {/* Empty State */}
            {!loading && !error && projects.length === 0 ? (
              <EmptyState
                title="Không tìm thấy dự án phù hợp"
                description="Thử thay đổi năm thực hiện, từ khóa hoặc điều kiện lọc ở cột bên trái."
              />
            ) : null}

            {/* Cards Grid */}
            {projects.length > 0 ? (
              <>
                <div className="project-archive-grid" aria-busy={loading}>
                  {projects.map((project) => (
                    <ProjectCard project={project} key={project.id} />
                  ))}
                </div>

                {/* Incremental Load More Action (No Pagination) */}
                {remainingCount > 0 ? (
                  <div className="project-archive-bottom-actions">
                    <button
                      type="button"
                      className="project-load-more-btn"
                      onClick={handleLoadMore}
                      disabled={loadingMore}
                    >
                      {loadingMore ? (
                        <>
                          <Loader2 size={16} className="spin-animate" aria-hidden="true" />
                          <span>Đang tải thêm...</span>
                        </>
                      ) : (
                        <>
                          <span>Xem thêm</span>
                          <ChevronDown size={16} aria-hidden="true" />
                        </>
                      )}
                    </button>
                  </div>
                ) : null}
              </>
            ) : null}
          </main>
        </div>
      </div>
    </div>
  )
}

function ProjectCard({ project }: { project: PublicProjectSummary }) {
  const leaders = project.leaders ?? []
  const fields = project.researchFields ?? []
  const description = project.description || project.goal || 'Thông tin chi tiết đang được cập nhật.'
  const publicStatus = project.publicStatus
  const coverUrl = project.coverUrl

  const projectYear = project.startDate
    ? new Date(project.startDate).getFullYear()
    : new Date().getFullYear()

  const leaderName = leaders.length > 0 ? leaders[0].name : null

  return (
    <Link className={`project-card ${coverUrl ? 'has-cover' : ''}`} to={`/du-an/${project.id}`}>
      {/* Optional Cover Image Banner */}
      {coverUrl ? (
        <div className="project-card-cover">
          <img src={coverUrl} alt={project.name} loading="lazy" />
          <div className="project-card-cover-badges">
            <span className="project-card-type-badge">
              {PROJECT_TYPE_LABELS[project.projectType] ?? 'Đề tài'}
            </span>
            <span className={`project-status-badge status-${publicStatus.toLowerCase()}`}>
              {PUBLIC_PROJECT_STATUS_LABELS[publicStatus]}
            </span>
          </div>
        </div>
      ) : null}

      <div className="project-card-inner">
        {/* Top Badges Row for non-cover cards */}
        {!coverUrl && (
          <div className="project-card-top-row">
            <div className="project-card-top-left">
              <span className="project-card-type-badge">
                {PROJECT_TYPE_LABELS[project.projectType] ?? 'Đề tài'}
              </span>
            </div>

            <div className="project-card-top-right">
              <span className={`project-status-badge status-${publicStatus.toLowerCase()}`}>
                {PUBLIC_PROJECT_STATUS_LABELS[publicStatus]}
              </span>
            </div>
          </div>
        )}

        {/* Project Title */}
        <h3 className="project-card-title">{project.name}</h3>

        {/* Project Description */}
        <p className="project-card-desc">{description}</p>

        {/* Tech / Research Field Chips */}
        {fields.length > 0 && (
          <div className="project-card-chips">
            {fields.slice(0, 3).map((f) => (
              <span className="project-tech-chip" key={f.id}>
                {f.name}
              </span>
            ))}
            {fields.length > 3 && (
              <span className="project-tech-chip chip-more">+{fields.length - 3}</span>
            )}
          </div>
        )}

        {/* Card Footer: Leader & Year */}
        <div className="project-card-footer">
          <div className="project-card-leader-info">
            {leaderName ? (
              <>
                <span className="project-card-avatar" aria-hidden="true">
                  {initialsOf(leaderName)}
                </span>
                <span className="project-card-leader-name">{leaderName}</span>
              </>
            ) : (
              <span className="project-card-no-leader">
                <UsersRound size={13} aria-hidden="true" /> Chưa phân công
              </span>
            )}
          </div>

          <div className="project-card-year-info">
            <Calendar size={13} aria-hidden="true" />
            <span>{projectYear}</span>
          </div>
        </div>
      </div>

      {/* Hover Overlay at Card Bottom: Dark Gradient Inner Shadow */}
      <div className="project-card-hover-overlay" aria-hidden="true">
        <span className="project-card-hover-action">Xem chi tiết dự án →</span>
        <span className="project-card-hover-hint">Nhấp để mở</span>
      </div>
    </Link>
  )
}

function asProjectType(value: string | null): ProjectType | 'ALL' {
  return value && PROJECT_TYPES.includes(value as ProjectType) ? (value as ProjectType) : 'ALL'
}

function asPublicProjectStatus(value: string | null): PublicProjectStatus {
  return value && PUBLIC_PROJECT_STATUSES.includes(value as PublicProjectStatus)
    ? (value as PublicProjectStatus)
    : 'RECRUITING'
}

function projectListError(reason: unknown) {
  return reason instanceof ApiClientError && reason.message
    ? reason.message
    : 'Không thể tải danh sách dự án. Vui lòng thử lại.'
}

function initialsOf(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part.charAt(0))
    .join('')
    .toLocaleUpperCase('vi')
}
