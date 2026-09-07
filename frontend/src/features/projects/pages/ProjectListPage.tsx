import { ArrowRight, RotateCcw, Search, UsersRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Pagination } from "../../../shared/components/Pagination";

import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import type { ResearchField } from '../../../shared/types/api'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { ApiClientError } from '../../../lib/apiClient'
import { getResearchFields } from '../../profile/api'
import { PublicPageHead } from '../../public/components/PublicPageHead'
import { listPublicProjects } from '../api'
import {
  PROJECT_TYPE_LABELS,
  PROJECT_TYPES,
  PUBLIC_PROJECT_STATUS_LABELS,
  PUBLIC_PROJECT_STATUSES,
} from '../types'
import type { PublicProjectSummary, ProjectType, PublicProjectStatus } from '../types'
import './ProjectListPage.css'

const PAGE_SIZE = 12

type FilterValue = 'ALL' | string

export function ProjectListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [researchFields, setResearchFields] = useState<ResearchField[]>([])
  const [fieldsError, setFieldsError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const currentPage = parsePage(searchParams.get('page'))
  const query = searchParams.get('q') ?? ''
  const typeFilter = asProjectType(searchParams.get('projectType'))
  const statusFilter = asPublicProjectStatus(searchParams.get('status'))
  const fieldFilter = searchParams.get('field') ?? 'ALL'

  useEffect(() => {
    let active = true
    void getResearchFields()
      .then((result) => {
        if (active) setResearchFields(result)
      })
      .catch((reason: unknown) => {
        if (active) setFieldsError(reason instanceof Error ? reason.message : 'Không tải được bộ lọc lĩnh vực.')
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    void listPublicProjects(currentPage - 1, PAGE_SIZE, {
      query,
      researchFieldCode: fieldFilter === 'ALL' ? undefined : fieldFilter,
      projectType: typeFilter === 'ALL' ? undefined : typeFilter,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
    }, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setProjects(result.items)
        setTotalElements(result.totalElements)
        setTotalPages(result.totalPages)
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setProjects([])
          setTotalElements(0)
          setTotalPages(0)
          setError(projectListError(reason))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [currentPage, fieldFilter, query, reloadKey, statusFilter, typeFilter])

  const hasFilters = Boolean(query.trim()) || typeFilter !== 'ALL' || statusFilter !== 'ALL' || fieldFilter !== 'ALL'
  const fieldOptions = useMemo(() => [
    { value: 'ALL', label: fieldsError ? 'Không tải được lĩnh vực' : 'Tất cả lĩnh vực' },
    ...researchFields.map((field) => ({ value: field.code, label: field.name })),
  ], [fieldsError, researchFields])

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

  return (
    <>
      <PublicPageHead
        title="Dự án"
        description="Khám phá các dự án nghiên cứu và sản phẩm công khai của Smart Lab."
      />

      <section className="section project-directory-section">
        <div className="wrap">
          <div className="project-directory-intro">
            <div>
              <div className="kicker">PROJECT ARCHIVE</div>
              <h2>Dự án Smart Lab</h2>
              <p>Chọn theo lĩnh vực, trạng thái hoặc loại dự án để tìm hướng đi phù hợp.</p>
            </div>
            <span className="project-directory-page-size">12 dự án / trang</span>
          </div>

          <div className="project-directory-toolbar" role="search">
            <label className="project-directory-search">
              <span className="sr-only">Tìm kiếm dự án</span>
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => updateFilter('q', event.target.value)}
                placeholder="Tìm theo mã, tên hoặc nội dung..."
              />
            </label>

            <PopupSelect
              value={typeFilter}
              options={[{ value: 'ALL', label: 'Tất cả loại dự án' }, ...PROJECT_TYPES.map((type) => ({ value: type, label: PROJECT_TYPE_LABELS[type] }))]}
              onChange={(value) => updateFilter('projectType', value)}
              ariaLabel="Lọc theo loại dự án"
              className="project-directory-filter"
            />

            <PopupSelect
              value={fieldFilter}
              options={fieldOptions}
              onChange={(value) => updateFilter('field', value)}
              ariaLabel="Lọc theo lĩnh vực nghiên cứu"
              className="project-directory-filter"
              disabled={Boolean(fieldsError)}
            />

            <PopupSelect
              value={statusFilter}
              options={[{ value: 'ALL', label: 'Tất cả trạng thái' }, ...PUBLIC_PROJECT_STATUSES.map((status) => ({ value: status, label: PUBLIC_PROJECT_STATUS_LABELS[status] }))]}
              onChange={(value) => updateFilter('status', value)}
              ariaLabel="Lọc theo nhóm trạng thái"
              className="project-directory-filter"
            />

            {hasFilters ? (
              <button className="project-directory-reset" type="button" onClick={resetFilters}>
                <RotateCcw size={15} aria-hidden="true" />
                Đặt lại
              </button>
            ) : null}
          </div>

          {error ? (
            <div className="project-directory-request-state" role="alert">
              <Feedback error={error} />
              <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            </div>
          ) : null}

          {loading && projects.length === 0 ? (
            <div className="public-empty empty tight project-directory-request-state" aria-busy="true">Đang tải danh sách dự án...</div>
          ) : null}

          {!loading && !error && projects.length === 0 ? (
            <EmptyState
              title={hasFilters ? 'Không tìm thấy dự án phù hợp' : 'Chưa có dự án công khai'}
              description={hasFilters ? 'Thử thay đổi từ khóa hoặc bộ lọc.' : 'Các dự án công khai của Smart Lab sẽ xuất hiện tại đây.'}
            />
          ) : null}

          {projects.length > 0 ? (
            <>
              <div className="project-directory-results-bar">
                <p className="project-directory-summary" aria-live="polite">
                  {formatRange(currentPage, PAGE_SIZE, totalElements)} · {totalElements} dự án
                </p>
                {loading ? <span className="project-directory-refreshing" aria-live="polite">Đang cập nhật...</span> : null}
              </div>
              <div className="project-directory-grid" aria-busy={loading}>
                {projects.map((project) => <ProjectCard project={project} key={project.id} />)}
              </div>
              <Pagination page={currentPage} totalPages={totalPages} onChange={updatePage} />
            </>
          ) : null}
        </div>
      </section>
    </>
  )
}

function ProjectCard({ project }: { project: PublicProjectSummary }) {
  const leaders = project.leaders ?? []
  const fields = project.researchFields ?? []
  const shownLeaders = leaders.slice(0, 3)
  const remainingLeaderCount = leaders.length - shownLeaders.length
  const description = project.description || project.goal || 'Thông tin chi tiết đang được cập nhật.'
  const publicStatus = project.publicStatus

  return (
    <Link className="project-directory-card" to={`/du-an/${project.id}`}>
      <div className="project-directory-card-body">
        <div className="project-directory-card-topline">
          <span className="project-directory-card-code">{project.code}</span>
          <span className="project-directory-card-public-status">{PUBLIC_PROJECT_STATUS_LABELS[publicStatus]}</span>
        </div>

        <div className="project-directory-card-badges">
          <span className="project-directory-card-type">{PROJECT_TYPE_LABELS[project.projectType]}</span>
          {project.publicStatus === 'RECRUITING' ? <span className="project-directory-card-recruiting">Đang tuyển</span> : null}
        </div>

        <h2 className="project-directory-card-title">{project.name}</h2>
        <p className="project-directory-card-description">{description}</p>

        {fields.length > 0 ? (
          <div className="project-directory-card-fields" aria-label="Lĩnh vực nghiên cứu">
            {fields.slice(0, 2).map((field) => <span className="chip" key={field.id}>{field.name}</span>)}
            {fields.length > 2 ? <span className="chip">+{fields.length - 2}</span> : null}
          </div>
        ) : null}

        <div className="project-directory-card-footer">
          {shownLeaders.length > 0 ? (
            <span className="project-directory-card-leaders" aria-label={`${leaders.length} leader`}>
              {shownLeaders.map((leader, index) => <span className="ava xs" title={leader.name} key={`${leader.name}-${index}`}>{initialsOf(leader.name)}</span>)}
              {remainingLeaderCount > 0 ? <span className="ava xs">+{remainingLeaderCount}</span> : null}
            </span>
          ) : (
            <span className="project-directory-card-no-leader"><UsersRound size={15} aria-hidden="true" /> Chưa phân công leader</span>
          )}
          <span className="project-directory-card-detail">Xem chi tiết <ArrowRight size={16} aria-hidden="true" /></span>
        </div>
      </div>
    </Link>
  )
}


function asProjectType(value: string | null): ProjectType | 'ALL' {
  return value && PROJECT_TYPES.includes(value as ProjectType) ? value as ProjectType : 'ALL'
}

function asPublicProjectStatus(value: string | null): PublicProjectStatus | 'ALL' {
  return value && PUBLIC_PROJECT_STATUSES.includes(value as PublicProjectStatus) ? value as PublicProjectStatus : 'ALL'
}

function parsePage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function formatRange(page: number, size: number, total: number) {
  if (total === 0) return '0 / 0'
  return `${(page - 1) * size + 1}–${Math.min(page * size, total)} / ${total}`
}

function projectListError(reason: unknown) {
  return reason instanceof ApiClientError && reason.message
    ? reason.message
    : 'Không thể tải danh sách dự án. Vui lòng thử lại.'
}

function initialsOf(name: string) {
  return name.trim().split(/\s+/).filter(Boolean).slice(-2).map((part) => part.charAt(0)).join('').toLocaleUpperCase('vi')
}
