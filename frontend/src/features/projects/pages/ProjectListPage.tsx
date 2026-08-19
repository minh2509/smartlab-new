import { ArrowRight, RotateCcw, Search, UsersRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import type { ResearchField } from '../../../shared/types/api'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { useAuth } from '../../auth/authContext'
import { getResearchFields } from '../../profile/api'
import { PublicPageHead } from '../../public/components/PublicPageHead'
import { listProjects } from '../api'
import {
  PROJECT_STATUSES,
  PROJECT_STATUS_BADGES,
  PROJECT_STATUS_LABELS,
  PROJECT_TYPES,
  PROJECT_TYPE_LABELS,
} from '../types'
import type { Project, ProjectStatus, ProjectType } from '../types'
import './ProjectListPage.css'

type TypeFilter = ProjectType | 'ALL'
type StatusFilter = ProjectStatus | 'ALL'
type ResearchFieldFilter = number | 'ALL'

export function ProjectListPage() {
  const { token } = useAuth()
  const [projects, setProjects] = useState<Project[]>([])
  const [researchFields, setResearchFields] = useState<ResearchField[]>([])
  const [query, setQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [researchFieldFilter, setResearchFieldFilter] = useState<ResearchFieldFilter>('ALL')
  const [loading, setLoading] = useState(true)
  const [fieldsLoading, setFieldsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [fieldsError, setFieldsError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)

    listProjects(token, {
      researchFieldId: researchFieldFilter === 'ALL' ? undefined : researchFieldFilter,
    })
      .then((result) => {
        if (active) setProjects(result)
      })
      .catch((reason: unknown) => {
        if (active) {
          setError(reason instanceof Error ? reason.message : 'Không tải được danh sách dự án.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [reloadKey, researchFieldFilter, token])

  useEffect(() => {
    let active = true
    setFieldsLoading(true)
    setFieldsError(null)
    void getResearchFields()
      .then((result) => {
        if (active) setResearchFields(result)
      })
      .catch((reason: unknown) => {
        if (active) {
          setFieldsError(reason instanceof Error ? reason.message : 'Không tải được bộ lọc lĩnh vực.')
        }
      })
      .finally(() => {
        if (active) setFieldsLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const visibleProjects = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('vi')

    return projects.filter((project) => {
      if (typeFilter !== 'ALL' && project.projectType !== typeFilter) return false
      if (statusFilter !== 'ALL' && project.status !== statusFilter) return false
      if (!normalizedQuery) return true

      const searchableText = [
        project.code,
        project.name,
        project.description ?? '',
        project.goal ?? '',
        ...project.leaders.map((leader) => leader.name),
      ]
        .join(' ')
        .toLocaleLowerCase('vi')

      return searchableText.includes(normalizedQuery)
    })
  }, [projects, query, statusFilter, typeFilter])

  const hasActiveFilters = Boolean(query.trim())
    || typeFilter !== 'ALL'
    || statusFilter !== 'ALL'
    || researchFieldFilter !== 'ALL'

  const typeOptions = [
    { value: 'ALL', label: 'Tất cả loại dự án' },
    ...PROJECT_TYPES.map((type) => ({ value: type, label: PROJECT_TYPE_LABELS[type] })),
  ]
  const statusOptions = [
    { value: 'ALL', label: 'Tất cả trạng thái' },
    ...PROJECT_STATUSES.map((status) => ({ value: status, label: PROJECT_STATUS_LABELS[status] })),
  ]
  const researchFieldOptions = [
    {
      value: 'ALL',
      label: fieldsLoading
        ? 'Đang tải lĩnh vực...'
        : fieldsError
          ? 'Không tải được lĩnh vực'
          : 'Tất cả lĩnh vực',
    },
    ...(!fieldsLoading && !fieldsError
      ? researchFields.map((field) => ({ value: String(field.id), label: field.name }))
      : []),
  ]

  function resetFilters() {
    setQuery('')
    setTypeFilter('ALL')
    setStatusFilter('ALL')
    setResearchFieldFilter('ALL')
  }

  return (
    <>
      <PublicPageHead
        title="Dự án"
        description="Các dự án nghiên cứu và sản phẩm đang được phát triển tại Smart Lab."
      />

      <section className="section project-directory-section">
        <div className="wrap">
          <div className="project-directory-toolbar">
            <label className="project-directory-search">
              <span className="sr-only">Tìm kiếm dự án</span>
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Tìm theo mã, tên, mô tả hoặc leader..."
              />
            </label>

            <PopupSelect
              value={typeFilter}
              options={typeOptions}
              onChange={(value) => setTypeFilter(value as TypeFilter)}
              ariaLabel="Lọc theo loại dự án"
              className="project-directory-filter"
            />

            <PopupSelect
              value={String(researchFieldFilter)}
              options={researchFieldOptions}
              disabled={fieldsLoading || Boolean(fieldsError)}
              onChange={(value) => setResearchFieldFilter(value === 'ALL' ? 'ALL' : Number(value))}
              ariaLabel="Lọc theo lĩnh vực nghiên cứu"
              className="project-directory-filter"
            />

            <PopupSelect
              value={statusFilter}
              options={statusOptions}
              onChange={(value) => setStatusFilter(value as StatusFilter)}
              ariaLabel="Lọc theo trạng thái dự án"
              className="project-directory-filter"
            />

            {hasActiveFilters ? (
              <button
                className="project-directory-reset"
                type="button"
                onClick={resetFilters}
              >
                <RotateCcw size={15} aria-hidden="true" />
                Đặt lại
              </button>
            ) : null}
          </div>

          {error ? (
            <div className="project-directory-request-state">
              <Feedback error={error} />
              <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>
                Thử tải lại
              </button>
            </div>
          ) : null}

          {loading ? (
            <div className="public-empty empty tight project-directory-request-state">Đang tải danh sách dự án...</div>
          ) : null}

          {!loading && !error && visibleProjects.length > 0 ? (
            <>
              <p className="project-directory-summary" aria-live="polite">
                {hasActiveFilters
                  ? `${visibleProjects.length} kết quả${visibleProjects.length !== projects.length ? ` trong ${projects.length} dự án` : ''}`
                  : `${projects.length} dự án`}
              </p>
              <div className="project-directory-grid">
                {visibleProjects.map((project) => <ProjectCard project={project} key={project.id} />)}
              </div>
            </>
          ) : null}

          {!loading && !error && visibleProjects.length === 0 ? (
            <>
              <EmptyState
                title={projects.length === 0 ? 'Chưa có dự án bạn có thể xem' : 'Không tìm thấy dự án phù hợp'}
                description={
                  projects.length === 0
                    ? 'Dự án công khai và dự án nội bộ bạn được phép xem sẽ xuất hiện tại đây.'
                    : 'Thử thay đổi từ khóa hoặc bộ lọc.'
                }
              />
              {hasActiveFilters ? (
                <div className="project-directory-empty-action">
                  <button className="project-directory-reset" type="button" onClick={resetFilters}>
                    <RotateCcw size={15} aria-hidden="true" />
                    Đặt lại
                  </button>
                </div>
              ) : null}
            </>
          ) : null}
        </div>
      </section>
    </>
  )
}

function ProjectCard({ project }: { project: Project }) {
  const shownLeaders = project.leaders.slice(0, 3)
  const remainingLeaderCount = project.leaders.length - shownLeaders.length

  return (
    <Link className="project-directory-card" to={`/du-an/${project.id}`}>
      <div className="project-directory-card-body">
        <div className="project-directory-card-topline">
          <span className="project-directory-card-code">{project.code}</span>
          {project.isFeatured ? <span className="project-directory-card-featured">Nổi bật</span> : null}
        </div>

        <div className="project-directory-card-badges">
          <span className={`badge ${PROJECT_STATUS_BADGES[project.status]}`}>
            <span className="dot" />
            {PROJECT_STATUS_LABELS[project.status]}
          </span>
          <span className="project-directory-card-type">{PROJECT_TYPE_LABELS[project.projectType]}</span>
          {!project.isPublic ? <span className="project-directory-card-internal">Nội bộ</span> : null}
        </div>

        <h3 className="project-directory-card-title">{project.name}</h3>
        <p className={`project-directory-card-description${project.description ? '' : ' is-fallback'}`}>
          {project.description || 'Dự án chưa có mô tả.'}
        </p>

        <div className="project-directory-card-footer">
          {shownLeaders.length > 0 ? (
            <span className="project-directory-card-leaders" aria-label={`${project.leaders.length} leader`}>
              {shownLeaders.map((leader) => (
                <span className="ava xs" title={leader.name} key={leader.userId}>
                  {initialsOf(leader.name)}
                </span>
              ))}
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
